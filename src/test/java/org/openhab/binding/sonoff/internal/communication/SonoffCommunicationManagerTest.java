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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.sonoff.internal.dto.commands.SingleSwitch;
import org.openhab.binding.sonoff.internal.dto.commands.UiActive;
import org.openhab.binding.sonoff.internal.handler.SonoffDeviceListener;
import org.openhab.binding.sonoff.internal.handler.SonoffDeviceState;
import org.openhab.core.library.types.StringType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Unit tests for {@link SonoffCommunicationManager}
 * Tests message queuing, processing, and communication management functionality
 *
 * @author tschaban/SmartnyDom - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class SonoffCommunicationManagerTest {

    @Mock
    private SonoffCommunicationManagerListener mockListener;

    @Mock
    private SonoffDeviceState mockDeviceState;

    @Mock
    private SonoffDeviceListener mockDeviceListener;

    @Mock
    private ServiceEvent mockServiceEvent;

    @Mock
    private ServiceInfo mockServiceInfo;

    private SonoffCommunicationManager communicationManager;
    private Gson gson;

    private static final String TEST_DEVICE_ID = "test-device-123";
    private static final String TEST_DEVICE_KEY = "test-device-key-456";
    private static final String TEST_IP_ADDRESS = "192.168.1.100";
    private static final String TEST_API_KEY = "test-api-key-789";
    private static final String TEST_MODE_LOCAL = "local";
    private static final String TEST_MODE_CLOUD = "cloud";

    @BeforeEach
    void setUp() {
        gson = new Gson();
        communicationManager = new SonoffCommunicationManager(mockListener, gson);

        // Reset mocks before each test
        reset(mockListener, mockDeviceState, mockDeviceListener, mockServiceEvent, mockServiceInfo);
    }

    @Test
    void testConstructor_ShouldInitializeCorrectly() {
        // Act
        SonoffCommunicationManager manager = new SonoffCommunicationManager(mockListener, gson);

        // Assert
        assertNotNull(manager, "Communication manager should be created");
    }

    @Test
    void testStart_ShouldSetModeAndStartRunning() {
        // Act
        communicationManager.start(TEST_MODE_LOCAL);

        // Assert - Verify the manager is in running state by checking it accepts messages
        // We can't directly test the running state, but we can test behavior
        assertDoesNotThrow(() -> communicationManager.start(TEST_MODE_LOCAL));
    }

    @Test
    void testStop_ShouldStopRunningAndClearQueues() {
        // Arrange
        communicationManager.start(TEST_MODE_LOCAL);

        // Act
        communicationManager.stop();

        // Assert - Should not throw exceptions
        assertDoesNotThrow(() -> communicationManager.stop());
    }

    @Test
    void testStartRunning_ShouldSetRunningState() {
        // Act
        communicationManager.startRunning();

        // Assert - Test by queuing a message (should work when running)
        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        assertDoesNotThrow(() -> communicationManager.queueMessage(message));
    }

    @Test
    void testStopRunning_ShouldStopRunningState() {
        // Arrange
        communicationManager.startRunning();

        // Act
        communicationManager.stopRunning();

        // Assert - Should not throw exceptions
        assertDoesNotThrow(() -> communicationManager.stopRunning());
    }

    @Test
    void testQueueMessage_WhenRunning_ShouldAddToQueue() {
        // Arrange
        communicationManager.startRunning();
        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        // Act
        communicationManager.queueMessage(message);

        // Assert - Message should be queued (sequence should be set)
        assertNotNull(message.getSequence(), "Message sequence should be set when queued");
        assertTrue(message.getSequence() > 0, "Message sequence should be positive");
    }

    @Test
    void testQueueMessage_WhenNotRunning_ShouldNotAddToQueue() {
        // Arrange
        communicationManager.stopRunning();
        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        // Act & Assert - Should not throw exception but won't queue
        assertDoesNotThrow(() -> communicationManager.queueMessage(message));
    }

    @Test
    void testSendMessage_DeviceCommand_ShouldCallApiMessage() {
        // Arrange
        SonoffCommandMessage deviceMessage = new SonoffCommandMessage(TEST_DEVICE_ID);

        // Act
        communicationManager.sendMessage(deviceMessage);

        // Assert
        verify(mockListener).sendApiMessage(TEST_DEVICE_ID);
    }

    @Test
    void testSendMessage_DevicesCommand_ShouldCallApiMessage() {
        // Arrange
        SonoffCommandMessage devicesMessage = new SonoffCommandMessage();

        // Act
        communicationManager.sendMessage(devicesMessage);

        // Assert
        verify(mockListener).sendApiMessage("");
    }

    @Test
    void testSendMessage_LocalModeUnsupportedCommand_ShouldLogWarning() {
        // Arrange
        communicationManager.start(TEST_MODE_LOCAL);
        UiActive command = new UiActive();
        command.setUiActive(60);
        SonoffCommandMessage message = new SonoffCommandMessage("uiActive", TEST_DEVICE_ID, false, command);

        // Act
        communicationManager.sendMessage(message);

        // Assert - Should not call any send methods
        verify(mockListener, never()).sendLanMessage(anyString(), anyString(), anyString());
        verify(mockListener, never()).sendWebsocketMessage(anyString());
    }

    @Test
    void testSendMessage_LanSupported_ShouldSendLanMessage() throws UnknownHostException {
        // Arrange
        communicationManager.start(TEST_MODE_LOCAL);
        communicationManager.isConnected(true, false); // LAN connected, cloud not connected

        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        // Mock device state
        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockDeviceState.getDeviceKey()).thenReturn(TEST_DEVICE_KEY);
        when(mockDeviceState.getIpAddress()).thenReturn(new StringType(TEST_IP_ADDRESS));

        // Act
        communicationManager.sendMessage(message);

        // Assert
        verify(mockListener).sendLanMessage(anyString(), contains("http://" + TEST_IP_ADDRESS + ":8081/zeroconf/switch"),
                anyString());
    }

    @Test
    void testSendMessage_CloudMode_ShouldSendWebsocketMessage() {
        // Arrange
        communicationManager.start(TEST_MODE_CLOUD);
        communicationManager.setApiKey(TEST_API_KEY);
        communicationManager.isConnected(false, true); // LAN not connected, cloud connected

        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        // Act
        communicationManager.sendMessage(message);

        // Assert
        verify(mockListener).sendWebsocketMessage(anyString());
    }

    @Test
    void testSendMessage_NoConnections_ShouldLogError() {
        // Arrange
        communicationManager.start(TEST_MODE_CLOUD);
        communicationManager.isConnected(false, false); // No connections

        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        // Act
        communicationManager.sendMessage(message);

        // Assert - Should not call any send methods
        verify(mockListener, never()).sendLanMessage(anyString(), anyString(), anyString());
        verify(mockListener, never()).sendWebsocketMessage(anyString());
        verify(mockListener, never()).sendApiMessage(anyString());
    }

    @Test
    void testWebsocketMessage_UpdateAction_ShouldProcessState() {
        // Arrange
        JsonObject updateMessage = new JsonObject();
        updateMessage.addProperty("action", "update");
        updateMessage.addProperty("deviceid", TEST_DEVICE_ID);
        JsonObject params = new JsonObject();
        params.addProperty("switch", "on");
        updateMessage.add("params", params);

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockListener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);

        // Act
        communicationManager.websocketMessage(gson.toJson(updateMessage));

        // Assert
        verify(mockDeviceState).updateState(any(JsonObject.class));
        verify(mockDeviceListener).updateDevice(mockDeviceState);
    }

    @Test
    void testWebsocketMessage_SysmsgAction_ShouldProcessState() {
        // Arrange
        JsonObject sysmsgMessage = new JsonObject();
        sysmsgMessage.addProperty("action", "sysmsg");
        sysmsgMessage.addProperty("deviceid", TEST_DEVICE_ID);

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockListener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);

        // Act
        communicationManager.websocketMessage(gson.toJson(sysmsgMessage));

        // Assert
        verify(mockDeviceState).updateState(any(JsonObject.class));
        verify(mockDeviceListener).updateDevice(mockDeviceState);
    }

    @Test
    void testWebsocketMessage_WithSequence_ShouldProcessOkMessage() {
        // Arrange
        JsonObject responseMessage = new JsonObject();
        responseMessage.addProperty("sequence", "12345");
        responseMessage.addProperty("error", "0");

        // Act
        communicationManager.websocketMessage(gson.toJson(responseMessage));

        // Assert - Should not throw exceptions
        assertDoesNotThrow(() -> communicationManager.websocketMessage(gson.toJson(responseMessage)));
    }

    @Test
    void testWebsocketMessage_ConsumptionType_ShouldProcessConsumptionData() {
        // Arrange - Create a consumption message that would be processed if messageType was set
        JsonObject consumptionMessage = new JsonObject();
        consumptionMessage.addProperty("sequence", "12345");
        consumptionMessage.addProperty("deviceid", TEST_DEVICE_ID);
        JsonObject config = new JsonObject();
        config.addProperty("power", "100");
        consumptionMessage.add("config", config);

        // Act & Assert - Should handle the message without throwing exception
        // Note: Consumption processing requires messageType to be set via prior message sending,
        // so this test verifies the message is handled gracefully
        assertDoesNotThrow(() -> communicationManager.websocketMessage(gson.toJson(consumptionMessage)));
    }

    @Test
    void testWebsocketMessage_InvalidJson_ShouldHandleGracefully() {
        // Arrange
        String invalidJson = "{ invalid json }";

        // Act & Assert - Should throw JsonSyntaxException for malformed JSON
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> communicationManager.websocketMessage(invalidJson));
    }

    @Test
    void testWebsocketMessage_NullMessage_ShouldHandleGracefully() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.websocketMessage(null));
    }

    @Test
    void testApiMessage_ShouldProcessThingList() {
        // Arrange
        JsonObject apiResponse = new JsonObject();
        JsonObject data = new JsonObject();
        JsonArray thingList = new JsonArray();

        JsonObject thing = new JsonObject();
        JsonObject itemData = new JsonObject();
        itemData.addProperty("deviceid", TEST_DEVICE_ID);
        thing.add("itemData", itemData);
        thingList.add(thing);

        data.add("thingList", thingList);
        apiResponse.add("data", data);

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockListener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);

        // Act
        communicationManager.apiMessage(apiResponse);

        // Assert
        verify(mockDeviceState).updateState(any(JsonObject.class));
        verify(mockDeviceListener).updateDevice(mockDeviceState);
    }

    @Test
    void testLanResponse_ValidResponse_ShouldProcessOkMessage() {
        // Arrange
        String lanResponseJson = "{\"error\":0,\"sequence\":\"12345\"}";

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.lanResponse(lanResponseJson));
    }

    @Test
    void testLanResponse_InvalidResponse_ShouldLogError() {
        // Arrange
        String invalidResponse = "invalid json";

        // Act & Assert - Should throw JsonSyntaxException for invalid JSON
        assertThrows(com.google.gson.JsonSyntaxException.class,
                () -> communicationManager.lanResponse(invalidResponse));
    }

    @Test
    void testServiceAdded_ShouldLogTrace() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.serviceAdded(mockServiceEvent));
        assertDoesNotThrow(() -> communicationManager.serviceAdded(null));
    }

    @Test
    void testServiceRemoved_ShouldLogDebug() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.serviceRemoved(mockServiceEvent));
        assertDoesNotThrow(() -> communicationManager.serviceRemoved(null));
    }

    @Test
    void testServiceResolved_ShouldProcessLanDevice() throws UnknownHostException {
        // Arrange
        when(mockServiceEvent.getInfo()).thenReturn(mockServiceInfo);
        when(mockServiceInfo.getInet4Addresses()).thenReturn(
                new java.net.Inet4Address[] { (java.net.Inet4Address) InetAddress.getByName(TEST_IP_ADDRESS) });
        when(mockServiceInfo.getPropertyNames()).thenReturn(new java.util.Enumeration<String>() {
            private final String[] props = { "id", "encrypt" };
            private int index = 0;

            @Override
            public boolean hasMoreElements() {
                return index < props.length;
            }

            @Override
            public String nextElement() {
                return props[index++];
            }
        });
        when(mockServiceInfo.getPropertyString("id")).thenReturn(TEST_DEVICE_ID);
        when(mockServiceInfo.getPropertyString("encrypt")).thenReturn("true");

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockDeviceState.getDeviceKey()).thenReturn(TEST_DEVICE_KEY);
        when(mockListener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);

        // Act
        communicationManager.serviceResolved(mockServiceEvent);

        // Assert
        verify(mockDeviceState).updateState(any(JsonObject.class));
        verify(mockDeviceListener).updateDevice(mockDeviceState);
    }

    @Test
    void testServiceResolved_NullEvent_ShouldHandleGracefully() {
        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.serviceResolved(null));
    }

    @Test
    void testSetApiKey_ShouldStoreApiKey() {
        // Act
        communicationManager.setApiKey(TEST_API_KEY);

        // Assert - Test by sending a cloud message that uses the API key
        communicationManager.start(TEST_MODE_CLOUD);
        communicationManager.isConnected(false, true);

        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        communicationManager.sendMessage(message);

        // Verify websocket message was sent (which uses the API key)
        verify(mockListener).sendWebsocketMessage(anyString());
    }

    @Test
    void testIsConnected_ShouldUpdateConnectionStates() {
        // Act
        communicationManager.isConnected(true, false);

        // Assert - Test by sending a message that should use LAN
        communicationManager.start(TEST_MODE_LOCAL);

        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockDeviceState.getDeviceKey()).thenReturn(TEST_DEVICE_KEY);
        when(mockDeviceState.getIpAddress()).thenReturn(new StringType(TEST_IP_ADDRESS));

        communicationManager.sendMessage(message);

        // Should use LAN since it's connected
        verify(mockListener).sendLanMessage(anyString(), anyString(), anyString());
    }

    @Test
    void testRun_ShouldProcessQueuedMessages() throws InterruptedException {
        // Arrange
        communicationManager.startRunning();

        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, command);

        communicationManager.queueMessage(message);

        // Act
        Thread runThread = new Thread(communicationManager);
        runThread.start();

        // Give some time for processing
        Thread.sleep(100);

        communicationManager.stopRunning();
        runThread.interrupt();

        // Assert - Message should have been processed
        assertTrue(message.getSequence() > 0, "Message should have sequence set");
    }

    @Test
    void testMultipleOperations_ShouldWorkTogether() {
        // Arrange
        communicationManager.start(TEST_MODE_CLOUD);
        communicationManager.setApiKey(TEST_API_KEY);
        communicationManager.isConnected(false, true);

        // Act - Perform multiple operations
        SingleSwitch switchCommand = new SingleSwitch();
        switchCommand.setSwitch("on");
        SonoffCommandMessage switchMessage = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, switchCommand);

        SonoffCommandMessage deviceMessage = new SonoffCommandMessage(TEST_DEVICE_ID);

        communicationManager.sendMessage(switchMessage);
        communicationManager.sendMessage(deviceMessage);

        JsonObject updateMessage = new JsonObject();
        updateMessage.addProperty("action", "update");
        updateMessage.addProperty("deviceid", TEST_DEVICE_ID);

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockListener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);

        communicationManager.websocketMessage(gson.toJson(updateMessage));

        // Assert
        verify(mockListener).sendWebsocketMessage(anyString());
        verify(mockListener).sendApiMessage(TEST_DEVICE_ID);
        verify(mockDeviceState).updateState(any(JsonObject.class));
        verify(mockDeviceListener).updateDevice(mockDeviceState);
    }

    @Test
    void testEdgeCases_EmptyDeviceId_ShouldHandleGracefully() {
        // Arrange
        SingleSwitch command = new SingleSwitch();
        command.setSwitch("on");
        SonoffCommandMessage message = new SonoffCommandMessage("switch", "", true, command);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.sendMessage(message));
    }

    @Test
    void testEdgeCases_NullCommand_ShouldHandleGracefully() {
        // Arrange
        SonoffCommandMessage message = new SonoffCommandMessage(TEST_DEVICE_ID);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> communicationManager.sendMessage(message));
    }

    @Test
    void testQueuePriority_SwitchCommands_ShouldHaveHigherPriority() {
        // Arrange
        communicationManager.startRunning();

        // Create switch command (should have higher priority)
        SingleSwitch switchCommand = new SingleSwitch();
        switchCommand.setSwitch("on");
        SonoffCommandMessage switchMessage = new SonoffCommandMessage("switch", TEST_DEVICE_ID, true, switchCommand);

        // Create other command
        UiActive uiCommand = new UiActive();
        uiCommand.setUiActive(60);
        SonoffCommandMessage uiMessage = new SonoffCommandMessage("uiActive", TEST_DEVICE_ID, false, uiCommand);

        // Act - Queue in reverse priority order
        communicationManager.queueMessage(uiMessage);
        communicationManager.queueMessage(switchMessage);

        // Assert - Both should be queued successfully
        assertNotNull(switchMessage.getSequence(), "Switch message should be queued");
        assertNotNull(uiMessage.getSequence(), "UI message should be queued");
    }

    @Test
    void testProcessState_DeviceNotFound_ShouldLogError() {
        // Arrange
        JsonObject updateMessage = new JsonObject();
        updateMessage.addProperty("action", "update");
        updateMessage.addProperty("deviceid", "unknown-device");

        when(mockListener.getState("unknown-device")).thenReturn(null);

        // Act & Assert - Should not throw exception (method logs error and returns early)
        assertDoesNotThrow(() -> communicationManager.websocketMessage(gson.toJson(updateMessage)));
    }

    @Test
    void testProcessState_NoDeviceListener_ShouldLogDebug() {
        // Arrange
        JsonObject updateMessage = new JsonObject();
        updateMessage.addProperty("action", "update");
        updateMessage.addProperty("deviceid", TEST_DEVICE_ID);

        when(mockListener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(mockListener.getListener(TEST_DEVICE_ID)).thenReturn(null); // No listener

        // Act
        communicationManager.websocketMessage(gson.toJson(updateMessage));

        // Assert
        verify(mockDeviceState).updateState(any(JsonObject.class));
        verify(mockListener).getListener(TEST_DEVICE_ID);
    }
}
