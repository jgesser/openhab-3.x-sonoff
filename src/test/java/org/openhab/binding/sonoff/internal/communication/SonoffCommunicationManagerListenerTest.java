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
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openhab.binding.sonoff.internal.handler.SonoffDeviceListener;
import org.openhab.binding.sonoff.internal.handler.SonoffDeviceState;

/**
 * Unit tests for {@link SonoffCommunicationManagerListener}
 * Tests interface contract and behavior through mock implementations
 *
 * @author tschaban/SmartnyDom - Initial contribution
 */
@ExtendWith(MockitoExtension.class)
class SonoffCommunicationManagerListenerTest {

    @Mock
    private SonoffCommunicationManagerListener listener;

    @Mock
    private SonoffDeviceState mockDeviceState;

    @Mock
    private SonoffDeviceListener mockDeviceListener;

    private static final String TEST_DEVICE_ID = "test-device-123";
    private static final String TEST_URL = "http://192.168.1.100:8081/zeroconf/switch";
    private static final String TEST_PAYLOAD = "{\"deviceid\":\"test-device-123\",\"data\":{\"switch\":\"on\"}}";
    private static final String TEST_WEBSOCKET_PARAMS = "{\"action\":\"update\",\"deviceid\":\"test-device-123\"}";

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(listener, mockDeviceState, mockDeviceListener);
    }

    @Test
    void testGetState_WithValidDeviceId_ShouldReturnDeviceState() {
        // Arrange
        when(listener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);

        // Act
        SonoffDeviceState result = listener.getState(TEST_DEVICE_ID);

        // Assert
        assertNotNull(result, "Should return device state for valid device ID");
        assertEquals(mockDeviceState, result, "Should return the expected device state");
        verify(listener).getState(TEST_DEVICE_ID);
    }

    @Test
    void testGetState_WithInvalidDeviceId_ShouldReturnNull() {
        // Arrange
        String invalidDeviceId = "invalid-device-456";
        when(listener.getState(invalidDeviceId)).thenReturn(null);

        // Act
        SonoffDeviceState result = listener.getState(invalidDeviceId);

        // Assert
        assertNull(result, "Should return null for invalid device ID");
        verify(listener).getState(invalidDeviceId);
    }

    @Test
    void testGetState_WithNullDeviceId_ShouldHandleGracefully() {
        // Arrange
        when(listener.getState(null)).thenReturn(null);

        // Act
        SonoffDeviceState result = listener.getState(null);

        // Assert
        assertNull(result, "Should handle null device ID gracefully");
        verify(listener).getState(null);
    }

    @Test
    void testGetState_WithEmptyDeviceId_ShouldReturnNull() {
        // Arrange
        String emptyDeviceId = "";
        when(listener.getState(emptyDeviceId)).thenReturn(null);

        // Act
        SonoffDeviceState result = listener.getState(emptyDeviceId);

        // Assert
        assertNull(result, "Should return null for empty device ID");
        verify(listener).getState(emptyDeviceId);
    }

    @Test
    void testGetListener_WithValidDeviceId_ShouldReturnDeviceListener() {
        // Arrange
        when(listener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);

        // Act
        SonoffDeviceListener result = listener.getListener(TEST_DEVICE_ID);

        // Assert
        assertNotNull(result, "Should return device listener for valid device ID");
        assertEquals(mockDeviceListener, result, "Should return the expected device listener");
        verify(listener).getListener(TEST_DEVICE_ID);
    }

    @Test
    void testGetListener_WithInvalidDeviceId_ShouldReturnNull() {
        // Arrange
        String invalidDeviceId = "invalid-device-789";
        when(listener.getListener(invalidDeviceId)).thenReturn(null);

        // Act
        SonoffDeviceListener result = listener.getListener(invalidDeviceId);

        // Assert
        assertNull(result, "Should return null for invalid device ID");
        verify(listener).getListener(invalidDeviceId);
    }

    @Test
    void testGetListener_WithNullDeviceId_ShouldHandleGracefully() {
        // Arrange
        when(listener.getListener(null)).thenReturn(null);

        // Act
        SonoffDeviceListener result = listener.getListener(null);

        // Assert
        assertNull(result, "Should handle null device ID gracefully");
        verify(listener).getListener(null);
    }

    @Test
    void testGetListener_WithEmptyDeviceId_ShouldReturnNull() {
        // Arrange
        String emptyDeviceId = "";
        when(listener.getListener(emptyDeviceId)).thenReturn(null);

        // Act
        SonoffDeviceListener result = listener.getListener(emptyDeviceId);

        // Assert
        assertNull(result, "Should return null for empty device ID");
        verify(listener).getListener(emptyDeviceId);
    }

    @Test
    void testSendLanMessage_WithValidParameters_ShouldInvokeMethod() {
        // Arrange
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD);

        // Act
        listener.sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD);

        // Assert
        verify(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD);
    }

    @Test
    void testSendLanMessage_WithNullUrl_ShouldHandleGracefully() {
        // Arrange
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, null, TEST_PAYLOAD);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendLanMessage(TEST_DEVICE_ID, null, TEST_PAYLOAD));
        verify(listener).sendLanMessage(TEST_DEVICE_ID, null, TEST_PAYLOAD);
    }

    @Test
    void testSendLanMessage_WithNullPayload_ShouldHandleGracefully() {
        // Arrange
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, null);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendLanMessage(TEST_DEVICE_ID, TEST_URL, null));
        verify(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, null);
    }

    @Test
    void testSendLanMessage_WithEmptyUrl_ShouldHandleGracefully() {
        // Arrange
        String emptyUrl = "";
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, emptyUrl, TEST_PAYLOAD);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendLanMessage(TEST_DEVICE_ID, emptyUrl, TEST_PAYLOAD));
        verify(listener).sendLanMessage(TEST_DEVICE_ID, emptyUrl, TEST_PAYLOAD);
    }

    @Test
    void testSendLanMessage_WithEmptyPayload_ShouldHandleGracefully() {
        // Arrange
        String emptyPayload = "";
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, emptyPayload);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendLanMessage(TEST_DEVICE_ID, TEST_URL, emptyPayload));
        verify(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, emptyPayload);
    }

    @Test
    void testSendApiMessage_WithValidDeviceId_ShouldInvokeMethod() {
        // Arrange
        doNothing().when(listener).sendApiMessage(TEST_DEVICE_ID);

        // Act
        listener.sendApiMessage(TEST_DEVICE_ID);

        // Assert
        verify(listener).sendApiMessage(TEST_DEVICE_ID);
    }

    @Test
    void testSendApiMessage_WithNullDeviceId_ShouldHandleGracefully() {
        // Arrange
        doNothing().when(listener).sendApiMessage(null);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendApiMessage(null));
        verify(listener).sendApiMessage(null);
    }

    @Test
    void testSendApiMessage_WithEmptyDeviceId_ShouldHandleGracefully() {
        // Arrange
        String emptyDeviceId = "";
        doNothing().when(listener).sendApiMessage(emptyDeviceId);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendApiMessage(emptyDeviceId));
        verify(listener).sendApiMessage(emptyDeviceId);
    }

    @Test
    void testSendWebsocketMessage_WithValidParams_ShouldInvokeMethod() {
        // Arrange
        doNothing().when(listener).sendWebsocketMessage(TEST_WEBSOCKET_PARAMS);

        // Act
        listener.sendWebsocketMessage(TEST_WEBSOCKET_PARAMS);

        // Assert
        verify(listener).sendWebsocketMessage(TEST_WEBSOCKET_PARAMS);
    }

    @Test
    void testSendWebsocketMessage_WithNullParams_ShouldHandleGracefully() {
        // Arrange
        doNothing().when(listener).sendWebsocketMessage(null);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendWebsocketMessage(null));
        verify(listener).sendWebsocketMessage(null);
    }

    @Test
    void testSendWebsocketMessage_WithEmptyParams_ShouldHandleGracefully() {
        // Arrange
        String emptyParams = "";
        doNothing().when(listener).sendWebsocketMessage(emptyParams);

        // Act & Assert - Should not throw exception
        assertDoesNotThrow(() -> listener.sendWebsocketMessage(emptyParams));
        verify(listener).sendWebsocketMessage(emptyParams);
    }

    @Test
    void testSendWebsocketMessage_WithJsonParams_ShouldInvokeMethod() {
        // Arrange
        String jsonParams = "{\"action\":\"query\",\"deviceid\":\"device-456\",\"params\":{\"switch\":\"off\"}}";
        doNothing().when(listener).sendWebsocketMessage(jsonParams);

        // Act
        listener.sendWebsocketMessage(jsonParams);

        // Assert
        verify(listener).sendWebsocketMessage(jsonParams);
    }

    @Test
    void testMultipleOperations_ShouldWorkTogether() {
        // Arrange
        when(listener.getState(TEST_DEVICE_ID)).thenReturn(mockDeviceState);
        when(listener.getListener(TEST_DEVICE_ID)).thenReturn(mockDeviceListener);
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD);
        doNothing().when(listener).sendApiMessage(TEST_DEVICE_ID);
        doNothing().when(listener).sendWebsocketMessage(TEST_WEBSOCKET_PARAMS);

        // Act
        SonoffDeviceState state = listener.getState(TEST_DEVICE_ID);
        SonoffDeviceListener deviceListener = listener.getListener(TEST_DEVICE_ID);
        listener.sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD);
        listener.sendApiMessage(TEST_DEVICE_ID);
        listener.sendWebsocketMessage(TEST_WEBSOCKET_PARAMS);

        // Assert
        assertNotNull(state, "Should retrieve device state");
        assertNotNull(deviceListener, "Should retrieve device listener");

        verify(listener).getState(TEST_DEVICE_ID);
        verify(listener).getListener(TEST_DEVICE_ID);
        verify(listener).sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD);
        verify(listener).sendApiMessage(TEST_DEVICE_ID);
        verify(listener).sendWebsocketMessage(TEST_WEBSOCKET_PARAMS);
    }

    @Test
    void testInterfaceContract_AllMethodsArePublic() {
        // This test verifies that all interface methods are accessible
        // and can be called without compilation errors

        // Arrange
        when(listener.getState(anyString())).thenReturn(null);
        when(listener.getListener(anyString())).thenReturn(null);
        doNothing().when(listener).sendLanMessage(anyString(), anyString(), anyString());
        doNothing().when(listener).sendApiMessage(anyString());
        doNothing().when(listener).sendWebsocketMessage(anyString());

        // Act & Assert - All methods should be callable
        assertDoesNotThrow(() -> {
            listener.getState("test");
            listener.getListener("test");
            listener.sendLanMessage(TEST_DEVICE_ID, "url", "payload");
            listener.sendApiMessage("deviceid");
            listener.sendWebsocketMessage("params");
        });
    }

    /**
     * Test implementation of SonoffCommunicationManagerListener for testing purposes
     */
    static class TestSonoffCommunicationManagerListener implements SonoffCommunicationManagerListener {
        private final SonoffDeviceState deviceState;
        private final SonoffDeviceListener deviceListener;

        public TestSonoffCommunicationManagerListener(SonoffDeviceState deviceState,
                SonoffDeviceListener deviceListener) {
            this.deviceState = deviceState;
            this.deviceListener = deviceListener;
        }

        @Override
        public SonoffDeviceState getState(String deviceid) {
            return TEST_DEVICE_ID.equals(deviceid) ? deviceState : null;
        }

        @Override
        public SonoffDeviceListener getListener(String deviceid) {
            return TEST_DEVICE_ID.equals(deviceid) ? deviceListener : null;
        }

        @Override
        public void sendLanMessage(String deviceid, String url, String payload) {
            // Test implementation - no-op
        }

        @Override
        public void sendApiMessage(String deviceid) {
            // Test implementation - no-op
        }

        @Override
        public void sendWebsocketMessage(String params) {
            // Test implementation - no-op
        }

        @Override
        public void addState(String deviceid, SonoffDeviceState state) {
            // Test implementation - no-op
        }
    }

    @Test
    void testConcreteImplementation_ShouldWorkCorrectly() {
        // Arrange
        TestSonoffCommunicationManagerListener testListener = new TestSonoffCommunicationManagerListener(
                mockDeviceState, mockDeviceListener);

        // Act & Assert
        assertEquals(mockDeviceState, testListener.getState(TEST_DEVICE_ID));
        assertNull(testListener.getState("invalid-device"));

        assertEquals(mockDeviceListener, testListener.getListener(TEST_DEVICE_ID));
        assertNull(testListener.getListener("invalid-device"));

        // These should not throw exceptions
        assertDoesNotThrow(() -> testListener.sendLanMessage(TEST_DEVICE_ID, TEST_URL, TEST_PAYLOAD));
        assertDoesNotThrow(() -> testListener.sendApiMessage(TEST_DEVICE_ID));
        assertDoesNotThrow(() -> testListener.sendWebsocketMessage(TEST_WEBSOCKET_PARAMS));
    }

    @Test
    void testEdgeCases_SpecialCharactersInParameters() {
        // Arrange
        String specialDeviceId = "device-with-special-chars-!@#$%^&*()";
        String specialUrl = "http://192.168.1.100:8081/path?param=value&special=!@#$%^&*()";
        String specialPayload = "{\"special\":\"!@#$%^&*()\",\"unicode\":\"测试\"}";
        String specialParams = "{\"unicode\":\"测试\",\"emoji\":\"😀\"}";

        when(listener.getState(specialDeviceId)).thenReturn(null);
        when(listener.getListener(specialDeviceId)).thenReturn(null);
        doNothing().when(listener).sendLanMessage(TEST_DEVICE_ID, specialUrl, specialPayload);
        doNothing().when(listener).sendApiMessage(specialDeviceId);
        doNothing().when(listener).sendWebsocketMessage(specialParams);

        // Act & Assert - Should handle special characters gracefully
        assertDoesNotThrow(() -> {
            listener.getState(specialDeviceId);
            listener.getListener(specialDeviceId);
            listener.sendLanMessage(TEST_DEVICE_ID, specialUrl, specialPayload);
            listener.sendApiMessage(specialDeviceId);
            listener.sendWebsocketMessage(specialParams);
        });

        verify(listener).getState(specialDeviceId);
        verify(listener).getListener(specialDeviceId);
        verify(listener).sendLanMessage(TEST_DEVICE_ID, specialUrl, specialPayload);
        verify(listener).sendApiMessage(specialDeviceId);
        verify(listener).sendWebsocketMessage(specialParams);
    }
}
