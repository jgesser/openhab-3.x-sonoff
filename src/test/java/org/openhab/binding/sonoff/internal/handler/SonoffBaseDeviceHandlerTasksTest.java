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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.ThingHandlerCallback;
import org.openhab.core.types.Command;

/**
 * Unit tests for the start and cancel of the tasks of {@link SonoffBaseDeviceHandler}.
 *
 * The bridge callback and initialize() can both reach bridgeStatusChanged() at the same time while the bridge
 * comes online. Every task started twice polls the device twice, and a device that serves one connection at a time
 * answers the second request with an error.
 */
@NonNullByDefault
class SonoffBaseDeviceHandlerTasksTest {

    private static final ThingStatusInfo BRIDGE_ONLINE = new ThingStatusInfo(ThingStatus.ONLINE,
            ThingStatusDetail.NONE, null);
    private static final ThingStatusInfo BRIDGE_OFFLINE = new ThingStatusInfo(ThingStatus.OFFLINE,
            ThingStatusDetail.NONE, null);

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private TestHandler handler = new TestHandler(mock(Thing.class));

    /** Counts the starts and cancels, the first start waits until it is released. */
    private static class TestHandler extends SonoffBaseDeviceHandler {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger cancels = new AtomicInteger();
        final CountDownLatch firstStartRunning = new CountDownLatch(1);
        final CountDownLatch releaseFirstStart = new CountDownLatch(1);
        boolean blockFirstStart;

        TestHandler(Thing thing) {
            super(thing);
        }

        @Override
        public void startTasks() {
            if (starts.incrementAndGet() == 1 && blockFirstStart) {
                firstStartRunning.countDown();
                try {
                    releaseFirstStart.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void cancelTasks() {
            cancels.incrementAndGet();
        }

        @Override
        public void updateDevice(SonoffDeviceState newDevice) {
        }

        @Override
        public void handleCommand(ChannelUID channelUID, Command command) {
        }
    }

    @BeforeEach
    void setUp() {
        Thing thing = mock(Thing.class);
        lenient().when(thing.getUID()).thenReturn(new ThingUID("sonoff", "32", "test-meter"));
        handler = new TestHandler(thing);
        handler.setCallback(mock(ThingHandlerCallback.class));
        SonoffAccountHandler account = mock(SonoffAccountHandler.class);
        lenient().when(account.getMode()).thenReturn("mixed");
        handler.account = account;
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    @DisplayName("Two bridge callbacks at the same time start the tasks once")
    void testConcurrentBridgeStatusChangedStartsTasksOnce() throws Exception {
        handler.blockFirstStart = true;

        Future<?> first = executor.submit(() -> handler.bridgeStatusChanged(BRIDGE_ONLINE));
        assertTrue(handler.firstStartRunning.await(5, TimeUnit.SECONDS), "The first callback is starting the tasks");
        // the second callback arrives while the first one is still starting the tasks
        Future<?> second = executor.submit(() -> handler.bridgeStatusChanged(BRIDGE_ONLINE));
        Thread.sleep(300);
        handler.releaseFirstStart.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        assertEquals(1, handler.starts.get(), "The tasks of the device are started once");
    }

    @Test
    @DisplayName("The tasks start again after the bridge went offline")
    void testTasksRestartAfterBridgeOffline() {
        handler.bridgeStatusChanged(BRIDGE_ONLINE);
        handler.bridgeStatusChanged(BRIDGE_ONLINE);
        assertEquals(1, handler.starts.get(), "A repeated online callback does not start them again");

        handler.bridgeStatusChanged(BRIDGE_OFFLINE);
        assertEquals(1, handler.cancels.get());
        handler.bridgeStatusChanged(BRIDGE_ONLINE);

        assertEquals(2, handler.starts.get());
    }

    @Test
    @DisplayName("dispose cancels the tasks and allows a new start")
    void testDisposeCancelsTasks() {
        handler.bridgeStatusChanged(BRIDGE_ONLINE);
        handler.dispose();

        assertEquals(1, handler.cancels.get());
        assertFalse(handler.taskStarted);
    }
}
