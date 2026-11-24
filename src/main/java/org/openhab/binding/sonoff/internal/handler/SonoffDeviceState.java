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
package org.openhab.binding.sonoff.internal.handler;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.sonoff.internal.SonoffBindingConstants;
import org.openhab.core.library.types.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * The {@link SonoffDeviceState} contains the base state of a device
 *
 * @author David Murton - Initial contribution
 */

// Purpose: Gets devices states from payload json and set channel states
// params => payload parameters, parameters => Channels

@NonNullByDefault
public class SonoffDeviceState {
    private final Logger logger = LoggerFactory.getLogger(SonoffDeviceState.class);

    // Main Parameters
    private final String deviceKey;
    private final Integer uiid;
    private final String deviceid;
    // Properties
    private final String name;
    private final String brand;
    private final String model;
    private final String fw;
    // Connection
    private StringType ipAddress = new StringType("");
    private JsonArray subDevices = new JsonArray();
    private Boolean local = false;
    private Boolean cloud = false;
    // Parameters
    private final SonoffDeviceStateParameters parameters;

    public SonoffDeviceState(JsonObject device) {
        this.deviceid = device.get("deviceid").getAsString();
        this.uiid = device.getAsJsonObject("extra").get("uiid").getAsInt();
        this.deviceKey = device.get("devicekey").getAsString();
        this.name = device.get("name").getAsString();
        this.brand = device.get("brandName").getAsString();
        this.model = device.get("productModel").getAsString();
        JsonElement firmware = device.getAsJsonObject("params").get("fwVersion");
        this.fw = firmware != null ? firmware.getAsString() : "Not Applicable";
        this.parameters = new SonoffDeviceStateParameters();
        logger.info("-----------------------");
        logger.info("Found: {}", this.name);
        logger.info("Hardware: {}", this.brand + " Model: " + this.model + " FW: " + this.fw);
        logger.info("uiid: {}", this.uiid + ", DeviceId: " + this.deviceid);
        updateState(device);
    }

    public SonoffDeviceState updateState(JsonObject device) {
        if (device.get("ipaddress") != null) {
            setIpAddress(new StringType(device.get("ipaddress").getAsString()));
            setLocal(getIpAddress().toString().equals("") ? false : true);
        }
        if (device.get("online") != null) {
            setCloud(device.get("online").getAsBoolean());
        }
        JsonElement params = device.get("params");
        if (params != null && params.isJsonObject()) {
            JsonElement online = params.getAsJsonObject().get("online");
            if (online != null && !online.isJsonNull()) {
                setCloud(online.getAsBoolean());
            }
            setParameters(params.getAsJsonObject());
        }
        if (SonoffBindingConstants.createZigbeeBridgeMap().containsKey(uiid) || uiid.equals(28)) {
            setSubDevices(device);
        }
        return this;
    }

    public SonoffDeviceState getState() {
        return this;
    }

    public SonoffDeviceStateParameters getParameters() {
        return parameters;
    }

    private void setParameters(JsonObject params) {

        // Switches
        if (params.has("switch")) {
            // UUID 226 uses boolean, others use string
            if (uiid.equals(226)) {
                parameters.setSwitch0(params.get("switch").getAsBoolean() ? "on" : "off");
            } else {
                parameters.setSwitch0(params.get("switch").getAsString());
            }
        } else if (params.has("switches")) {
            JsonArray switches = params.getAsJsonArray("switches");

            for (int i = 0; i < switches.size(); i++) {
                JsonObject switchObj = switches.get(i).getAsJsonObject();

                if (switchObj.has("switch")) {
                    String switchState = switchObj.get("switch").getAsString();

                    // Zigbee multi-switch devices use 'outlet' field to specify which channel
                    // WiFi multi-switch devices rely on array index
                    int outletIndex = switchObj.has("outlet") ? switchObj.get("outlet").getAsInt() : i;

                    switch (outletIndex) {
                        case 0:
                            parameters.setSwitch0(switchState);
                            break;
                        case 1:
                            parameters.setSwitch1(switchState);
                            break;
                        case 2:
                            parameters.setSwitch2(switchState);
                            break;
                        case 3:
                            parameters.setSwitch3(switchState);
                            break;
                        default:
                            logger.warn(
                                    "Sonoff addon support only devices with at most 4 switches, ignoring switch: {}",
                                    outletIndex);
                    }
                }
            }
        }

        // Contact
        if (params.has("lock")) {
            if (params.get("lock") != null) {
                parameters.setContact0(params.get("lock").getAsInt());
            }
        }

        // Tamper
        if (params.has("split")) {
            if (params.get("split") != null) {
                parameters.setTamper(params.get("split").getAsInt());
            }
        }

        // Doors

        if (params.has("doorsensors")) {

            JsonObject doors = params.getAsJsonObject("doorsensors");

            if (doors.has("0")) { /* It set the doorSensor as well */
                parameters.setDoor0(doors.get("0").getAsString());
            }

            if (doors.has("1")) {
                parameters.setDoor1(doors.get("1").getAsString());
            }

            if (doors.has("2")) {
                parameters.setDoor2(doors.get("2").getAsString());
            }
        }

        // Electric or CAM2
        if (params.get("power") != null) {
            if (uiid.equals(256) || uiid.equals(260)) { // Temporary workaround to be fixed for CAMs
                parameters.setCamPower(params.get("power").getAsString());
            } else {
                if (uiid.equals(5) || uiid.equals(32)) {
                    parameters.setPower(params.get("power").getAsString(), 1f, 2);
                } else {
                    parameters.setPower(params.get("power").getAsString(), 0.01f, 2);
                }
            }
        }

        if (params.get("voltage") != null) {
            if (uiid.equals(5) || uiid.equals(32)) {
                parameters.setVoltage(params.get("voltage").getAsString(), 1f, 1);
            } else {
                parameters.setVoltage(params.get("voltage").getAsString(), 0.01f, 1);
            }
        }

        if (params.get("current") != null) {
            if (uiid.equals(5) || uiid.equals(32)) {
                parameters.setCurrent(params.get("current").getAsString(), 1f, 3);
            } else if (uiid.equals(276)) {
                // UUID 276 (WS01) reports current in centiamps (0.01 A increments)
                parameters.setCurrent(params.get("current").getAsString(), 0.01f, 3);
            } else {
                parameters.setCurrent(params.get("current").getAsString(), 0.001f, 3);
            }
        }

        // UUID 226 Circuit Breaker - phase_0_* fields
        if (uiid.equals(226)) {
            if (params.get("phase_0_p") != null) {
                parameters.setPower(params.get("phase_0_p").getAsString(), 1f, 2);
            }
            if (params.get("phase_0_v") != null) {
                parameters.setVoltage(params.get("phase_0_v").getAsString(), 1f, 2);
            }
            if (params.get("phase_0_c") != null) {
                parameters.setCurrent(params.get("phase_0_c").getAsString(), 1f, 2);
            }
            if (params.get("totalPower") != null) {
                parameters.setTotalPower(params.get("totalPower").getAsString(), 1f, 2);
            }
            if (params.get("availablePower") != null) {
                parameters.setAvailablePower(params.get("availablePower").getAsString(), 1f, 2);
            }
        }

        if (params.get("battery") != null) {
            // Set battery parameter based on device UUID
            if (uiid.equals(2026)) {
                // Motion Sensor uses battery voltage
                parameters.setBattery(params.get("battery").getAsDouble());
            } else if (uiid.equals(1770) || uiid.equals(7002) || uiid.equals(7003) || uiid.equals(7014)) {
                // Temperature/Humidity, Motion (SNZB-03P), and Door/Window sensors use battery level percentage
                parameters.setBatteryLevel(params.get("battery").getAsDouble());
            }
        }

        if (params.get("dayKwh") != null) {
            if (uiid.equals(5) || uiid.equals(32)) {
                parameters.setTodayKwh(params.get("dayKwh").getAsDouble(), 1f, 2);
            } else {
                parameters.setTodayKwh(params.get("dayKwh").getAsDouble(), 0.01f, 2);
            }
        }

        if (params.get("monthKwh") != null) {
            if (uiid.equals(5) || uiid.equals(32)) {
                parameters.setMonthKwh(params.get("monthKwh").getAsDouble(), 1f, 2);
            } else {
                parameters.setMonthKwh(params.get("monthKwh").getAsDouble(), 0.01f, 2);
            }
        }

        if (params.get("weekKwh") != null) {
            if (uiid.equals(5) || uiid.equals(32)) {
                parameters.setWeekKwh(params.get("weekKwh").getAsDouble(), 1f, 2);
            } else {
                parameters.setWeekKwh(params.get("weekKwh").getAsDouble(), 0.01f, 2);
            }
        }

        if (params.get("yearKwh") != null) {
            if (uiid.equals(5) || uiid.equals(32)) {
                parameters.setYearKwh(params.get("yearKwh").getAsDouble(), 1f, 2);
            } else {
                parameters.setYearKwh(params.get("yearKwh").getAsDouble(), 0.01f, 2);
            }
        }

        // Cost parameters (UUID 276) - divided by 100
        if (params.get("costDay") != null) {
            parameters.setCostDay(params.get("costDay").getAsDouble(), 0.01f, 2);
        }

        if (params.get("costWeek") != null) {
            parameters.setCostWeek(params.get("costWeek").getAsDouble(), 0.01f, 2);
        }

        if (params.get("costMonth") != null) {
            parameters.setCostMonth(params.get("costMonth").getAsDouble(), 0.01f, 2);
        }

        if (params.get("costYear") != null) {
            parameters.setCostYear(params.get("costYear").getAsDouble(), 0.01f, 2);
        }

        // Runtime (UUID 276)
        if (params.get("dayOnDuration") != null) {
            parameters.setDayRuntime(params.get("dayOnDuration").getAsInt());
        }

        if (params.get("monthOnDuration") != null) {
            parameters.setMonthRuntime(params.get("monthOnDuration").getAsInt());
        }

        // Energy
        if (params.get("hundredDaysKwhData") != null) {
            String kwhData = params.get("hundredDaysKwhData").getAsString();
            if (!kwhData.equals("get")) {
                String[] hexValues;
                String[] splitData = kwhData.split("(?<=\\G.{6})");
                double total = 0.00;
                for (int i = 0; i < 100; i++) {
                    hexValues = splitData[i].split("(?<=\\G.{2})");
                    total = total + Double.parseDouble(Integer.parseInt(hexValues[0], 16) + "."
                            + Integer.parseInt(hexValues[1], 16) + Integer.parseInt(hexValues[2], 16));
                    if (i == 0) {
                        if (uiid.equals(5) || uiid.equals(32)) {
                            parameters.setTodayKwh(total, 1f, 2);
                        } else {
                            parameters.setTodayKwh(total, 0.01f, 2);
                        }
                    }
                    if (i == 1) {
                        double newtotal = Double.parseDouble(Integer.parseInt(hexValues[0], 16) + "."
                                + Integer.parseInt(hexValues[1], 16) + Integer.parseInt(hexValues[2], 16));

                        if (uiid.equals(5) || uiid.equals(32)) {
                            parameters.setYesterdayKwh(newtotal, 1f, 2);
                        } else {
                            parameters.setYesterdayKwh(newtotal, 0.01f, 2);
                        }
                    }
                    if (i == 6) {
                        if (uiid.equals(5) || uiid.equals(32)) {
                            parameters.setSevenKwh(total, 1f, 2);
                        } else {
                            parameters.setSevenKwh(total, 0.01f, 2);
                        }
                    }
                    if (i == 29) {
                        if (uiid.equals(5) || uiid.equals(32)) {
                            parameters.setThirtyKwh(total, 1f, 2);
                        } else {
                            parameters.setThirtyKwh(total, 0.01f, 2);
                        }
                    }
                    if (i == 99) {
                        if (uiid.equals(5) || uiid.equals(32)) {
                            parameters.setHundredKwh(total, 1f, 2);
                        } else {
                            parameters.setHundredKwh(total, 0.01f, 2);
                        }
                    }
                }
            }
        }

        if (uiid.equals(15) || uiid.equals(181)) {
            // api returns a string always
            if (params.get("currentTemperature") != null) {
                JsonPrimitive p = params.get("currentTemperature").getAsJsonPrimitive();
                if (p.isString()) {
                    parameters.setTemperature(
                            p.getAsString().equals("unavailable") ? 0.00 : Double.parseDouble(p.getAsString()));
                }
            }
            // lan and websocket return a number most of the time
            if (params.get("temperature") != null) {
                JsonPrimitive p = params.get("temperature").getAsJsonPrimitive();
                if (p.isString()) {
                    parameters.setTemperature(
                            p.getAsString().equals("unavailable") ? 0.00 : Double.parseDouble(p.getAsString()));
                }
                if (p.isNumber()) {
                    parameters.setTemperature(p.getAsDouble());
                }
            }

            // api returns a string always
            if (params.get("currentHumidity") != null) {
                JsonPrimitive p = params.get("currentHumidity").getAsJsonPrimitive();
                if (p.isString()) {
                    parameters.setHumidity(
                            p.getAsString().equals("unavailable") ? 0.00 : Double.parseDouble(p.getAsString()));
                }
            }
            // lan and websocket return a number most of the time
            if (params.get("humidity") != null) {
                JsonPrimitive p = params.get("humidity").getAsJsonPrimitive();
                if (p.isString()) {
                    parameters.setHumidity(
                            p.getAsString().equals("unavailable") ? 0.00 : Double.parseDouble(p.getAsString()));
                }
                if (p.isNumber()) {
                    parameters.setHumidity(p.getAsDouble());
                }
            }
        } else if (!uiid.equals(266) && !uiid.equals(195) && !uiid.equals(278)) {
            // Temperature and humidity need /100 conversion for most devices
            // UUID 266 (SAWF-08P) is excluded and handled separately below
            if (params.get("temperature") != null) {
                parameters.setTemperature(Double.valueOf(params.get("temperature").getAsDouble() / 100));
            }

            if (params.get("humidity") != null) {
                parameters.setHumidity(Double.valueOf(params.get("humidity").getAsDouble() / 100));
            }

            // Temperature/Humidity statistics (UUID 7038 - SNZB-02DR2)
            if (params.get("temperatureMax") != null) {
                parameters.setTemperatureMax(Double.valueOf(params.get("temperatureMax").getAsDouble() / 100));
            }

            if (params.get("temperatureMin") != null) {
                parameters.setTemperatureMin(Double.valueOf(params.get("temperatureMin").getAsDouble() / 100));
            }

            if (params.get("temperatureAvg") != null) {
                parameters.setTemperatureAvg(Double.valueOf(params.get("temperatureAvg").getAsDouble() / 100));
            }

            if (params.get("humidityMax") != null) {
                parameters.setHumidityMax(Double.valueOf(params.get("humidityMax").getAsDouble() / 100));
            }

            if (params.get("humidityMin") != null) {
                parameters.setHumidityMin(Double.valueOf(params.get("humidityMin").getAsDouble() / 100));
            }

            if (params.get("humidityAvg") != null) {
                parameters.setHumidityAvg(Double.valueOf(params.get("humidityAvg").getAsDouble() / 100));
            }
        }

        // Air Quality Monitor (UUID 266 - SAWF-08P)
        if (uiid.equals(266)) {
            // Temperature and humidity are provided as float values (no /100 conversion needed)
            if (params.get("temperature") != null) {
                parameters.setTemperature(params.get("temperature").getAsDouble());
            }

            if (params.get("humidity") != null) {
                parameters.setHumidity(params.get("humidity").getAsDouble());
            }

            if (params.get("co2") != null) {
                parameters.setCo2(params.get("co2").getAsInt());
            }

            if (params.get("pm10") != null) {
                parameters.setPm10(params.get("pm10").getAsInt());
            }

            if (params.get("pm2_5") != null) {
                parameters.setPm2_5(params.get("pm2_5").getAsInt());
            }

            if (params.get("temperatureF") != null) {
                parameters.setTemperatureF(params.get("temperatureF").getAsDouble());
            }

            if (params.get("sensorLight") != null) {
                parameters.setSensorLight(params.get("sensorLight").getAsString());
            }

            if (params.get("sensorLightBr") != null) {
                parameters.setSensorLightBr(params.get("sensorLightBr").getAsInt());
            }

            if (params.get("voiceAlarm") != null) {
                parameters.setVoiceAlarm(params.get("voiceAlarm").getAsString());
            }
        }

        // NSPanel Pro (UUID 195) - temperature is a direct float value, versions are strings
        if (uiid.equals(195)) {
            if (params.get("temperature") != null) {
                parameters.setTemperature(params.get("temperature").getAsDouble());
            }
            if (params.get("fwVersion") != null) {
                parameters.setFwVersion(params.get("fwVersion").getAsString());
            }
            if (params.get("sysVersion") != null) {
                parameters.setSysVersion(params.get("sysVersion").getAsString());
            }
            if (params.get("zigbeeVersion") != null) {
                parameters.setZigbeeVersion(params.get("zigbeeVersion").getAsString());
            }
            if (params.get("appVersion") != null) {
                parameters.setAppVersion(params.get("appVersion").getAsString());
            }
        }

        // NSPanel Pro with Relay (UUID 278) - temperature, versions, CPU info, storage info, outdoor air quality
        if (uiid.equals(278)) {
            if (params.get("temperature") != null) {
                parameters.setTemperature(params.get("temperature").getAsDouble());
            }
            if (params.get("fwVersion") != null) {
                parameters.setFwVersion(params.get("fwVersion").getAsString());
            }
            if (params.get("sysVersion") != null) {
                parameters.setSysVersion(params.get("sysVersion").getAsString());
            }
            if (params.get("zigbeeVersion") != null) {
                parameters.setZigbeeVersion(params.get("zigbeeVersion").getAsString());
            }
            if (params.get("appVersion") != null) {
                parameters.setAppVersion(params.get("appVersion").getAsString());
            }
            if (params.get("cpuInfo") != null) {
                JsonObject cpuInfo = params.getAsJsonObject("cpuInfo");
                if (cpuInfo.get("temperature") != null) {
                    parameters.setCpuTemperature(cpuInfo.get("temperature").getAsDouble());
                }
            }
            if (params.get("storageInfo") != null) {
                JsonObject storageInfo = params.getAsJsonObject("storageInfo");
                if (storageInfo.get("total") != null) {
                    parameters.setStorageTotal(storageInfo.get("total").getAsString());
                }
                if (storageInfo.get("free") != null) {
                    parameters.setStorageFree(storageInfo.get("free").getAsString());
                }
                if (storageInfo.get("used") != null) {
                    parameters.setStorageUsed(storageInfo.get("used").getAsString());
                }
            }
            if (params.get("airQuality") != null) {
                JsonObject aq = params.getAsJsonObject("airQuality");
                if (aq.get("co") != null) {
                    parameters.setAirQualityCo(aq.get("co").getAsDouble());
                }
                if (aq.get("no2") != null) {
                    parameters.setAirQualityNo2(aq.get("no2").getAsDouble());
                }
                if (aq.get("o3") != null) {
                    parameters.setAirQualityO3(aq.get("o3").getAsDouble());
                }
                if (aq.get("so2") != null) {
                    parameters.setAirQualitySo2(aq.get("so2").getAsDouble());
                }
                if (aq.get("pm2_5") != null) {
                    parameters.setPm2_5((int) Math.round(aq.get("pm2_5").getAsDouble()));
                }
                if (aq.get("pm10") != null) {
                    parameters.setPm10((int) Math.round(aq.get("pm10").getAsDouble()));
                }
                if (aq.get("us-epa-index") != null) {
                    parameters.setUsEpaIndex(aq.get("us-epa-index").getAsInt());
                }
                if (aq.get("gb-defra-index") != null) {
                    parameters.setGbDefraIndex(aq.get("gb-defra-index").getAsInt());
                }
            }
        }

        // Sensors
        if (params.get("sensorType") != null) {
            parameters.setSensorType(params.get("sensorType").getAsString());
        }

        // Actions
        if (params.get("lastUpdate") != null) {
            parameters.setLastUpdate(params.get("lastUpdate").getAsString());
        }

        if (params.get("actionTime") != null) {
            parameters.setActionTime(params.get("actionTime").getAsString());
        }

        // RGB
        if (params.get("mode") != null) {
            parameters.setMode(params.get("mode").getAsInt());
            parameters.setMusicMode(params.get("mode").getAsInt() == 12 ? "on" : "off");
        }

        if (params.get("sensitive") != null) {
            parameters.setSensitivity(params.get("sensitive").getAsInt());
        }

        if (params.get("speed") != null) {
            parameters.setSpeed(params.get("speed").getAsInt());
        }

        // RGBIC (UUID 173) specific parameters
        if (params.get("rhythmMode") != null) {
            parameters.setRhythmMode(params.get("rhythmMode").getAsInt());
        }

        if (params.get("rhythmSensitive") != null) {
            parameters.setRhythmSensitivity(params.get("rhythmSensitive").getAsInt());
        }

        if (params.get("colorR") != null && params.get("colorG") != null && params.get("colorB") != null) {
            parameters.setColor(params.get("colorR").getAsInt(), params.get("colorG").getAsInt(),
                    params.get("colorB").getAsInt());
        }

        if (params.get("ltype") != null) {
            parameters.setLtype(params.get("ltype").getAsString());
        }

        if (params.get("bright") != null) {
            parameters.setColorBrightness(params.get("bright").getAsInt());
        }

        // Colour CCT Bulb

        // White setting
        if (params.get("white") != null) {
            // Color Temperature
            Integer colorTemperature = params.get("white").getAsJsonObject().get("ct").getAsInt();
            if (!colorTemperature.equals(0)) {
                Double d = Double.valueOf(colorTemperature);
                Double e = d / 255 * 100;
                colorTemperature = (int) Math.round(e);
            }
            parameters.setColorTemperature(colorTemperature);

            // Brightness
            parameters.setWhiteBrightness(params.get("white").getAsJsonObject().get("br").getAsInt());
        }

        // Color Setting
        if (params.get("color") != null) {
            // Color
            parameters.setColor(params.get("color").getAsJsonObject().get("r").getAsInt(),
                    params.get("color").getAsJsonObject().get("g").getAsInt(),
                    params.get("color").getAsJsonObject().get("b").getAsInt());
            // Brightness
            parameters.setColorBrightness(params.get("color").getAsJsonObject().get("br").getAsInt());
        }

        // Other
        if (params.get("sledOnline") != null) {
            parameters.setNetworkLED(params.get("sledOnline").getAsString());
        }

        // For WiFi devices use rssi, for Zigbee subdevices use subDevRssi
        // All Zigbee devices (non-bridge) use subDevRssi instead of rssi
        // Check if device is in Zigbee map (excludes bridges 66, 168, 243)
        if (SonoffBindingConstants.createZigbeeMap().containsKey(uiid)) {
            if (params.get("subDevRssi") != null) {
                parameters.setRssi(params.get("subDevRssi").getAsInt());
            }
        } else {
            if (params.get("rssi") != null) {
                parameters.setRssi(params.get("rssi").getAsInt());
            }
        }

        if (params.get("zled") != null) {
            parameters.setZigbeeLED(params.get("zled").getAsString());
        }

        // RF
        if (params.get("rfTrig0") != null) {
            parameters.setRf0(params.get("rfTrig0").getAsString());
        }

        if (params.get("rfTrig1") != null) {
            parameters.setRf1(params.get("rfTrig1").getAsString());
        }

        if (params.get("rfTrig2") != null) {
            parameters.setRf2(params.get("rfTrig2").getAsString());
        }

        if (params.get("rfTrig3") != null) {
            parameters.setRf3(params.get("rfTrig3").getAsString());
        }

        if (params.get("rfTrig4") != null) {
            parameters.setRf4(params.get("rfTrig4").getAsString());
        }

        if (params.get("rfTrig5") != null) {
            parameters.setRf5(params.get("rfTrig5").getAsString());
        }

        if (params.get("rfTrig6") != null) {
            parameters.setRf6(params.get("rfTrig6").getAsString());
        }

        if (params.get("rfTrig7") != null) {
            parameters.setRf7(params.get("rfTrig7").getAsString());
        }

        if (params.get("rfTrig8") != null) {
            parameters.setRf8(params.get("rfTrig8").getAsString());
        }

        if (params.get("rfTrig9") != null) {
            parameters.setRf9(params.get("rfTrig9").getAsString());
        }

        if (params.get("rfTrig10") != null) {
            parameters.setRf10(params.get("rfTrig10").getAsString());
        }

        if (params.get("rfTrig11") != null) {
            parameters.setRf11(params.get("rfTrig11").getAsString());
        }

        if (params.get("rfTrig12") != null) {
            parameters.setRf12(params.get("rfTrig12").getAsString());
        }

        if (params.get("rfTrig13") != null) {
            parameters.setRf13(params.get("rfTrig13").getAsString());
        }

        if (params.get("rfTrig14") != null) {
            parameters.setRf14(params.get("rfTrig14").getAsString());
        }

        if (params.get("rfTrig15") != null) {
            parameters.setRf15(params.get("rfTrig15").getAsString());
        }

        if (params.get("rfList") != null) {
            parameters.setRfCodeList(params.getAsJsonArray("rfList"));
        }

        // Zigbee

        if (params.get("trigTime") != null) {
            parameters.setTrigTime(params.get("trigTime").getAsString());
        }

        if (params.get("motion") != null) {
            parameters.setMotion(params.get("motion").getAsInt());
        }

        if (params.get("brState") != null) {
            parameters.setBrightnessState(params.get("brState").getAsString());
        }

        // Button press events
        if (params.get("key") != null && params.get("trigTime") != null) {
            Integer key = params.get("key").getAsInt();
            String trigTime = params.get("trigTime").getAsString();
            parameters.setButtonPress(key, trigTime);
        }

        // Roller Shutter (UUID 258)
        if (params.get("switch") != null && uiid.equals(258)) {
            parameters.setRollerSwitch(params.get("switch").getAsString());
        }

        if (params.get("setclose") != null) {
            parameters.setSetclose(params.get("setclose").getAsInt());
        }

        if (params.get("motorDir") != null) {
            parameters.setMotorDir(params.get("motorDir").getAsString());
        }

        if (params.get("swMode") != null) {
            parameters.setSwMode(params.get("swMode").getAsInt());
        }
    }

    public Map<String, String> getProperties() {
        Map<String, String> properties = new HashMap<>();
        properties.put("Name", name);
        properties.put("Brand", brand);
        properties.put("Model", model);
        properties.put("FW Version", fw);
        properties.put("Device ID", deviceid);
        properties.put("Device Key", deviceKey);
        properties.put("UIID", uiid.toString());
        properties.put("deviceid", deviceid);
        return properties;
    }

    public Integer getUiid() {
        return this.uiid;
    }

    public String getDeviceid() {
        return this.deviceid;
    }

    public Boolean getLocal() {
        return this.local;
    }

    public void setLocal(Boolean local) {
        this.local = local;
    }

    public Boolean getCloud() {
        return this.cloud;
    }

    public void setCloud(Boolean cloud) {
        this.cloud = cloud;
    }

    public String getDeviceKey() {
        return this.deviceKey;
    }

    public String getModel() {
        return this.model;
    }

    public StringType getIpAddress() {
        return this.ipAddress;
    }

    public void setIpAddress(StringType ipAddress) {
        this.ipAddress = ipAddress;
    }

    private void setSubDevices(JsonObject device) {
        JsonArray subDevices = null;
        if (SonoffBindingConstants.createZigbeeBridgeMap().containsKey(uiid)) {
            if (device.getAsJsonObject("params").getAsJsonArray("subDevices") != null) {
                subDevices = device.getAsJsonObject("params").getAsJsonArray("subDevices");
            }
        }
        if (uiid.equals(28)) {
            if (device.getAsJsonObject("tags") != null) {
                subDevices = device.getAsJsonObject("tags").getAsJsonArray("zyx_info");
            }
        }
        if (subDevices != null) {
            this.subDevices = subDevices;
        }
    }

    public JsonArray getSubDevices() {
        return subDevices;
    }
}
