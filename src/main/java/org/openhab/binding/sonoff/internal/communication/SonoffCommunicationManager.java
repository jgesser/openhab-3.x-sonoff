/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.sonoff.internal.communication;

import java.util.Enumeration;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.sonoff.internal.SonoffCacheProvider;
import org.openhab.binding.sonoff.internal.connection.SonoffConnectionManagerListener;
import org.openhab.binding.sonoff.internal.dto.commands.CircuitBreakerSwitch;
import org.openhab.binding.sonoff.internal.dto.commands.MultiSwitch;
import org.openhab.binding.sonoff.internal.dto.commands.SingleSwitch;
import org.openhab.binding.sonoff.internal.dto.requests.WebsocketRequest;
import org.openhab.binding.sonoff.internal.dto.responses.LanResponse;
import org.openhab.binding.sonoff.internal.handler.SonoffDeviceListener;
import org.openhab.binding.sonoff.internal.handler.SonoffDeviceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The {@link SonoffCommunicationManager} provides a sequential queue for outgoing messages accross connections and
 * allows for retrying when messages are not delivered correctly
 *
 * @author David Murton - Initial contribution
 */
@NonNullByDefault
public class SonoffCommunicationManager implements Runnable, SonoffConnectionManagerListener {

    private static final int QUEUE_SIZE = 100;

    private final Logger logger = LoggerFactory.getLogger(SonoffCommunicationManager.class);
    // Queue Utilities
    // Map of message retry attempts
    private final ConcurrentMap<Long, CountDownLatch> latchMap = new ConcurrentHashMap<>();
    // Queue of messages to send
    private final BlockingDeque<SonoffCommandMessage> queue = new LinkedBlockingDeque<>();
    // Map of Integers so we can count retry attempts.
    private final ConcurrentMap<Long, Integer> retryCountMap = new ConcurrentHashMap<>();
    // Map of our message types so we can process them correctly
    private final ConcurrentMap<Long, String> messageTypes = new ConcurrentHashMap<>();
    // Timeout
    private final int timeoutForOkMessagesMs = 1000;
    // Boolean to indicate if we are running
    private boolean running;

    private final Gson gson;
    private final SonoffCommunicationManagerListener listener;

    private String mode = "";
    private String apiKey = "";

    private Boolean lanConnected = false;
    private Boolean cloudConnected = false;

    public SonoffCommunicationManager(SonoffCommunicationManagerListener listener, Gson gson) {
        this.gson = gson;
        this.listener = listener;
    }

    public synchronized void start(String mode) {
        this.mode = mode;
        startRunning();
    }

    public synchronized void stop() {
        stopRunning();
        queue.clear();
        retryCountMap.clear();
        latchMap.clear();
        messageTypes.clear();
    }

    public void startRunning() {
        this.running = true;
    }

    public void stopRunning() {
        this.running = false;
    }

    @Override
    public void run() {
        logger.debug("Message queue is running");
        try {
            // Get the first message in the queue
            final @Nullable SonoffCommandMessage message = queue.take();
            logger.debug("Start processing: {}. {} messages remaining in queue", message.getCommand(), queue.size());

            if (message.getSequence().equals(0L)) {
                message.setSequence();
            }
            if (message.getCommand().equals("devices") || message.getCommand().equals("device")) {
                sendMessage(message);
                return;
            }
            if (message.getCommand().equals("consumption") || message.getCommand().equals("uiActive")) {
                messageTypes.put(message.getSequence(), message.getCommand());
                sendMessage(message);
            } else if (message.getCommand().equals("sledOnline") && message.getLanSupported()) {
                // The LAN poll is repeated every localPoll seconds, a lost one is not retried: retrying would
                // send overlapping requests to a device that serves one connection at a time
                sendMessage(message);
            } else {
                // Add our message type so we can identify it correctly when we get the response
                messageTypes.put(message.getSequence(), message.getCommand());
                CountDownLatch latch = new CountDownLatch(1);
                latchMap.putIfAbsent(message.getSequence(), latch);
                retryCountMap.putIfAbsent(message.getSequence(), Integer.valueOf(1));
                logger.debug("Sending message: command={}, sequence={}, deviceid={}", message.getCommand(),
                        message.getSequence(), message.getDeviceid());
                sendMessage(message);
                boolean unlatched = latch.await(timeoutForOkMessagesMs, TimeUnit.MILLISECONDS);
                latchMap.remove(message.getSequence());
                Integer newRetryCount = 0;
                if (!unlatched) {
                    Integer sendCount = retryCountMap.get(message.getSequence());
                    if (sendCount != null) {
                        if (sendCount.intValue() >= 3) {
                            logger.warn("Unable to send transaction {}, command was {}, device {}, after {} attempts",
                                    message.getSequence(), message.getCommand(), message.getDeviceid(), 3);
                            return;
                        }
                        newRetryCount = Integer.valueOf(sendCount.intValue() + 1);
                    }
                    if (!running) {
                        logger.error("Not retrying transactionId {} as we are stopping", message.getSequence());
                        return;

                    }
                    logger.warn(
                            "Ok message not received for transaction: {}, command was {}, device {}, retry count {}",
                            message.getSequence(), message.getCommand(), message.getDeviceid(), newRetryCount);
                    retryCountMap.put(message.getSequence(), newRetryCount);

                    addToQueue(message);
                }
            }
        } catch (InterruptedException e) {
            if (running) {
                logger.error("Error Running queue:{}", e.getMessage());
            }
        }
    }

    // Add the messsage to the queue
    public void queueMessage(SonoffCommandMessage message) {
        if (running) {
            message.setSequence();

            addToQueue(message);
        } else {
            logger.info("Message not added to queue as we are shutting down");
        }
    }

    private void addToQueue(SonoffCommandMessage message) {
        if (queue.size() >= QUEUE_SIZE) {
            logger.debug("Queue full, removing one message from queue, {} messages in queue", queue.size());
            queue.poll();
        }

        if (message.getCommand().equals("switch")) {
            // Check if it's a SingleSwitch or CircuitBreakerSwitch with a non-null switch value
            Object params = message.getParams();
            boolean hasSwitchValue = false;
            if (params instanceof SingleSwitch) {
                hasSwitchValue = ((SingleSwitch) params).getSwitch() != null;
            } else if (params instanceof CircuitBreakerSwitch) {
                hasSwitchValue = ((CircuitBreakerSwitch) params).getSwitch() != null;
            }
            if (hasSwitchValue) {
                queue.addFirst(message);
            } else {
                queue.add(message);
            }
        } else if (message.getCommand().equals("switches")
                && !((MultiSwitch) message.getParams()).getSwitches().isEmpty()) {
            queue.addFirst(message);
        } else {
            queue.add(message);
        }

        logger.debug("Added a message to the queue: {}, {} messages in queue", message.getCommand(), queue.size());
    }

    private void okMessage(Long sequence) {
        CountDownLatch latch = latchMap.get(sequence);
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Forward messages to the appropriate connection
     */
    public void sendMessage(SonoffCommandMessage message) {
        logger.debug("************* SEND MESSAGE START *************");
        logger.debug("Command: {}, DeviceID: {}, Sequence: {}", message.getCommand(), message.getDeviceid(),
                message.getSequence());
        logger.debug("LAN supported: {}, LAN connected: {}, Cloud connected: {}, Mode: {}", message.getLanSupported(),
                lanConnected, cloudConnected, mode);

        // Send Api Device requests
        if (message.getCommand().equals("device") || message.getCommand().equals("devices")) {
            logger.debug("API request - forwarding to API handler");
            listener.sendApiMessage(message.getDeviceid());
            logger.debug("************* SEND MESSAGE END (API) *************");
            return;
        }

        // Dont send commands if not supported by local mode
        if (!message.getLanSupported() && mode.equals("local")) {
            logger.warn("Cannot send command {} for device {}, Not supported by local mode", message.getCommand(),
                    message.getDeviceid());
            logger.debug("************* SEND MESSAGE END (UNSUPPORTED) *************");
            return;
        }

        // If local supported see if we can send it
        String ipaddress = "";
        String deviceKey = "";
        String url = "";
        if (message.getLanSupported() && lanConnected) {
            logger.debug("Attempting LAN transmission...");
            SonoffDeviceState state = listener.getState(message.getDeviceid());
            if (state != null) {
                deviceKey = state.getDeviceKey();
                ipaddress = state.getIpAddress().toString();
                if (!ipaddress.equals("")) {
                    url = "http://" + ipaddress + ":8081/zeroconf/" + message.getCommand();
                    logger.debug("Device state found - IP: {}, URL: {}", ipaddress, url);
                } else {
                    logger.debug("No IP address available for deviceid: {}", message.getDeviceid());
                }
            } else {
                logger.debug("Device state NOT found for deviceid: {}", message.getDeviceid());
            }

            // Send LAN Message
            if (!ipaddress.equals("")) {
                logger.debug("Sending message via LAN to: {}", url);
                String paramsJson = gson.toJson(message.getParams());
                logger.debug("LAN Command params JSON (before encryption): {}", paramsJson);
                listener.sendLanMessage(message.getDeviceid(), url,
                        new SonoffCommandMessageEncryptionUtilities().encrypt(paramsJson, deviceKey,
                                message.getDeviceid(), message.getSequence()));
                logger.debug("************* SEND MESSAGE END (LAN) *************");
                return;
            } else {
                logger.debug("LAN transmission skipped - no IP address available, falling back to cloud");
            }
        }

        // Send Websocket Message
        if (cloudConnected) {
            logger.debug("Sending message via Cloud WebSocket");

            // Special logging for consumption polling requests
            if (message.getCommand().equals("consumption")) {
                logger.info("========== CONSUMPTION POLLING REQUEST ==========");
                logger.info("Device ID: {}", message.getDeviceid());
                logger.info("Sequence: {}", message.getSequence());
                logger.info("Command: {}", message.getCommand());
            }

            String params = gson.toJson(message.getParams().getCommand());
            logger.debug("Command params JSON: {}", params);
            WebsocketRequest request = new WebsocketRequest(message.getSequence(), apiKey, message.getDeviceid(),
                    gson.fromJson(params, JsonObject.class));
            String fullRequest = gson.toJson(request);

            // Log full consumption request
            if (message.getCommand().equals("consumption")) {
                logger.info("Full consumption request JSON: {}", fullRequest);
                logger.info("=================================================");
            } else {
                logger.debug("Full WebSocket request JSON: {}", fullRequest);
            }

            listener.sendWebsocketMessage(fullRequest);
            logger.debug("************* SEND MESSAGE END (CLOUD) *************");
            return;
        }

        // Log if we cant send
        logger.error("************* SEND MESSAGE FAILED *************");
        logger.error("Cannot send command {}, all connections are offline for deviceid {}", message.getCommand(),
                message.getDeviceid());
        logger.error("LAN connected: {}, Cloud connected: {}, Mode: {}", lanConnected, cloudConnected, mode);
        logger.error("************* SEND MESSAGE END (OFFLINE) *************");
    }

    /**
     * Processes and forwards incoming states to the appropriate device handler
     */
    private synchronized void processState(JsonObject device, Boolean encrypted) {
        JsonElement deviceidElement = device.get("deviceid");
        if (deviceidElement == null || deviceidElement.isJsonNull()) {
            logger.warn("Cannot process state - device object missing 'deviceid' field: {}", device);
            return;
        }

        String deviceid = deviceidElement.getAsString();
        SonoffDeviceState state = listener.getState(deviceid);
        if (state == null) {
            logger.debug("The device {} doesnt exist, unable to set state", deviceid);
            return;
        }

        // LAN messages need to be decrypted first
        if (encrypted) {
            logger.trace("Decrypting LAN message for {}", deviceid);
            String ipAddress = device.get("localAddress") != null ? device.get("localAddress").getAsString() : "";
            JsonObject params = gson.fromJson(
                    new SonoffCommandMessageEncryptionUtilities().decrypt(device, state.getDeviceKey()),
                    JsonObject.class);
            device.add("params", params);
            if (!ipAddress.equals("")) {
                device.addProperty("ipaddress", ipAddress);
            }
            logger.trace("LAN message for {} is {}", deviceid, gson.toJson(device));
        }

        state.updateState(device);
        logger.debug("Updated state for {}, with data {}", deviceid, gson.toJson(device));
        SonoffDeviceListener deviceListener = listener.getListener(deviceid);
        if (deviceListener != null) {
            deviceListener.updateDevice(state);
            logger.trace("Forwarded state to device {}", deviceid);
        } else {
            logger.debug("Unable to forward state for {} as no listener present", deviceid);
        }
    }

    @Override
    public void websocketMessage(String message) {
        String messageType = "";
        String messageAction = "";
        String messageErrorCode = "";
        String messageError = "";

        JsonObject response = gson.fromJson(message, JsonObject.class);
        if (response != null) {
            JsonElement action = response.get("action"); // .getAsString();
            JsonElement seq = response.get("sequence"); // .getAsString();
            JsonElement errorCode = response.get("error"); // .getAsInt();
            if (response.get("reason") != null) {
                messageError = response.get("reason") != null ? response.get("reason").getAsString() : "No reason";
            }
            if (seq != null) {
                Long sequence = Long.parseLong(seq.getAsString());
                latchMap.get(sequence);
                String type = messageTypes.get(sequence);
                messageType = type != null ? type : messageType;
                messageTypes.remove(sequence);
                okMessage(sequence);
            }
            if (action != null) {
                messageAction = action.getAsString();
            }
            if (errorCode != null) {
                messageErrorCode = errorCode.getAsString();
            }

            // Process any state messages
            if (messageAction.equals("update") || messageAction.equals("sysmsg")) {
                JsonObject device = gson.fromJson(response, JsonObject.class);
                if (device != null) {
                    processState(device, false);
                }
                return;
            }

            // Process Other messages
            if (seq != null) {

                // Process streaming data activation
                if (messageType.equals("uiActive")) {
                    if (!messageErrorCode.equals("0")) {
                        JsonElement deviceid = response.get("deviceid");
                        String id = deviceid != null ? deviceid.getAsString() : "unknown";
                        logger.trace("Streaming Data Activation Error {} - {} ,For Device:{}", messageErrorCode,
                                messageError, id);
                    }

                    return;
                }

                // Consumption message
                if (messageType.equals("consumption")) {
                    logger.info("========== CONSUMPTION POLLING RESPONSE ==========");
                    logger.info("Full response JSON: {}", message);

                    JsonObject device = gson.fromJson(response, JsonObject.class);
                    if (device != null) {
                        JsonElement deviceidElement = device.get("deviceid");
                        String deviceid = deviceidElement != null && !deviceidElement.isJsonNull()
                                ? deviceidElement.getAsString()
                                : "unknown";
                        logger.info("Device ID: {}", deviceid);

                        // Safely get config field - may be null for some devices
                        JsonElement configElement = device.get("config");
                        logger.info("Config field present: {}, is null: {}", configElement != null,
                                configElement != null ? configElement.isJsonNull() : "n/a");

                        if (configElement != null && !configElement.isJsonNull()) {
                            try {
                                JsonObject params = configElement.getAsJsonObject();
                                logger.info("Consumption config data: {}", params.toString());
                                device.add("params", params);
                                processState(device, false);
                            } catch (Exception e) {
                                logger.error("Failed to parse consumption config for device {}: {}", deviceid,
                                        e.getMessage());
                                logger.error("Config element type: {}, value: {}", configElement.getClass().getName(),
                                        configElement);
                            }
                        } else {
                            logger.warn(
                                    "Consumption response for device {} has null/missing 'config' field - cannot process",
                                    deviceid);
                            logger.warn("Available fields in response: {}", device.keySet());
                        }
                    } else {
                        logger.error("Failed to parse consumption response as JsonObject");
                    }
                    logger.info("==================================================");
                    return;
                }

                // All other message (may need this for new device payloads)
                logger.trace("Websocket processed {} type message with payload {}", messageType, message);
            }
        } else {
            logger.error("Websocket message didnt have any content");
        }
    }

    @Override
    public void apiMessage(JsonObject thingResponse) {
        JsonElement dataElement = thingResponse.get("data");
        if (dataElement == null || dataElement.isJsonNull()) {
            logger.warn("API response missing 'data' field");
            return;
        }

        JsonObject data = dataElement.getAsJsonObject();
        JsonElement thingListElement = data.get("thingList");
        if (thingListElement == null || thingListElement.isJsonNull()) {
            logger.warn("API response missing 'thingList' field");
            return;
        }

        JsonArray thingList = thingListElement.getAsJsonArray();
        SonoffCacheProvider cacheProvider = new SonoffCacheProvider();
        for (int i = 0; i < thingList.size(); i++) {
            JsonElement thingElement = thingList.get(i);
            if (thingElement == null || thingElement.isJsonNull()) {
                logger.warn("Skipping null thing at index {}", i);
                continue;
            }

            JsonObject thing = thingElement.getAsJsonObject();
            JsonElement itemDataElement = thing.get("itemData");
            if (itemDataElement == null || itemDataElement.isJsonNull()) {
                logger.warn("Thing at index {} missing 'itemData' field", i);
                continue;
            }

            JsonObject device = itemDataElement.getAsJsonObject();

            // Auto-create cache file and add to in-memory map if it doesn't exist
            // This fixes cache path migration issues and ensures device is available immediately
            JsonElement deviceIdElement = device.get("deviceid");
            if (deviceIdElement != null && !deviceIdElement.isJsonNull()) {
                String deviceid = deviceIdElement.getAsString();
                SonoffDeviceState existingState = listener.getState(deviceid);
                if (existingState == null) {
                    // Create cache file if it doesn't exist
                    if (!cacheProvider.checkFile(deviceid)) {
                        cacheProvider.newFile(deviceid, gson.toJson(device));
                        logger.info("Auto-created cache file for device {} from API response", deviceid);
                    }
                    // Add state to in-memory map so device is immediately available
                    try {
                        SonoffDeviceState newState = new SonoffDeviceState(device);
                        listener.addState(deviceid, newState);
                        logger.info("Added device {} (uiid: {}) to in-memory state map", deviceid, newState.getUiid());
                    } catch (Exception e) {
                        logger.warn("Failed to create device state for {}: {}", deviceid, e.getMessage());
                    }
                }
            }

            processState(device, false);
        }
    }

    @Override
    public void lanResponse(String message) {
        LanResponse response = gson.fromJson(message, LanResponse.class);
        if (response != null) {
            okMessage(Long.parseLong(response.getSequence()));
            messageTypes.remove(Long.parseLong(response.getSequence()));
        } else {
            logger.error("LAN response returned null for message: {}", message);
        }
    }

    @Override
    public void serviceAdded(@Nullable ServiceEvent event) {
        if (event != null) {
            logger.trace("Sonoff - LAN Service added:{}", event.getInfo());
        }
    }

    @Override
    public void serviceRemoved(@Nullable ServiceEvent event) {
        if (event != null) {
            logger.debug("Sonoff - LAN Service removed:{}", event.getInfo());
            String deviceid = getDeviceId(event);
            if (deviceid != null) {
                setLocal(deviceid, false, "mDNS service removed");
            }
        }
    }

    @Override
    public void serviceResolved(@Nullable ServiceEvent event) {
        if (event != null) {
            ServiceInfo eventInfo = event.getInfo();
            String localAddress = eventInfo.getInet4Addresses()[0].getHostAddress();
            logger.trace("Lan event received from {} with payload {}", localAddress, eventInfo);
            JsonObject device = new JsonObject();
            Enumeration<String> info = eventInfo.getPropertyNames();
            while (info.hasMoreElements()) {
                final @Nullable String name = info.nextElement();
                String value = eventInfo.getPropertyString(name);
                device.addProperty(name.equals("id") ? "deviceid" : name, value);
            }
            device.addProperty("localAddress", localAddress.equals("null") ? "" : localAddress);
            String deviceid = device.get("deviceid").getAsString();
            SonoffDeviceState state = listener.getState(deviceid);
            boolean wasLocal = state != null && state.getLocal();
            processState(device, true);
            if (state != null && !wasLocal && state.getLocal()) {
                logger.info("Device {} is reachable on the LAN (mDNS service resolved)", deviceid);
            }
        }
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void isConnected(Boolean lanConnected, Boolean cloudConnected) {
        this.lanConnected = lanConnected;
        this.cloudConnected = cloudConnected;
    }

    @Override
    public void lanDeviceReachable(String deviceid, Boolean reachable, @Nullable String cause) {
        setLocal(deviceid, reachable, reachable ? "LAN request answered" : "LAN request failed: " + cause);
    }

    /**
     * Works out which device a mDNS service belongs to, the service name is eWeLink_&lt;deviceid&gt;
     */
    private @Nullable String getDeviceId(ServiceEvent event) {
        String name = event.getName();
        if (name != null && name.startsWith("eWeLink_")) {
            return name.substring("eWeLink_".length());
        }
        return event.getInfo().getPropertyString("id");
    }

    /**
     * Updates the LAN state of a device and forwards the change to its handler
     */
    private synchronized void setLocal(String deviceid, boolean local, String reason) {
        SonoffDeviceState state = listener.getState(deviceid);
        if (state == null || state.getLocal() == local) {
            return;
        }
        logger.info("Device {} is {} on the LAN ({})", deviceid, local ? "reachable" : "no longer reachable",
                reason);
        state.setLocal(local);
        SonoffDeviceListener deviceListener = listener.getListener(deviceid);
        if (deviceListener != null) {
            deviceListener.updateDevice(state);
        }
    }
}
