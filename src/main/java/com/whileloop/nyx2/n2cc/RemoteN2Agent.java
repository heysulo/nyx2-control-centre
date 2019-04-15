/*
 * The MIT License
 *
 * Copyright 2019 Team whileLOOP.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.whileloop.nyx2.n2cc;

import com.whileloop.nyx2.messages.HeartBeatMessage;
import com.whileloop.nyx2.messages.TerminationMessage;
import com.whileloop.nyx2.utils.NX2IntervalClock;
import com.whileloop.nyx2.utils.NX2Logger;
import com.whileloop.sendit.client.SClient;
import com.whileloop.sendit.messages.SMessage;
import io.netty.channel.nio.NioEventLoopGroup;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author sulochana
 */
public class RemoteN2Agent extends NX2Logger implements NX2IntervalClock.NX2IntervalClockCallback {

    private static final Map<UUID, RemoteN2Agent> connections = new HashMap<>();
    private static final NioEventLoopGroup agentEventLoopGroup = new NioEventLoopGroup();

    public synchronized static void registerN2A(UUID uuid, RemoteN2Agent agent) {
        connections.put(uuid, agent);
    }

    public synchronized static RemoteN2Agent findAgent(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return connections.get(uuid);
    }

    public synchronized static void deregisterAgent(UUID uuid) {
        if (uuid == null) {
            return;
        }
        connections.remove(uuid);
    }

    private final SClient client;
    private final UUID agentUUID;
    private final NX2IntervalClock hbScanner;
    private final int allowedHbMisses = 5;
    private long lastHB = 0x0;
    private int bhMissCount = 0;

    public RemoteN2Agent(SClient client, UUID uuid) {
        setVerboseLevel(Loglevel.DEBUG);
        this.client = client;
        this.agentUUID = uuid;
        this.hbScanner = new NX2IntervalClock(agentEventLoopGroup, this, 5, TimeUnit.SECONDS);
        this.client.attachUuid(uuid);
        registerN2A(uuid, this);
    }

    @Override
    public void OnInterval(NX2IntervalClock clock) {
        if (clock == this.hbScanner) {
            clockHbScannerFired();
        }
    }

    public void OnRemoteMessage(SMessage msg) {
        if (msg instanceof HeartBeatMessage) {
            handleHeartBeatMessage((HeartBeatMessage) msg);
        } else {
            debug("Unknown message ignored: %s", msg.getClass().getName());
        }
    }

    public synchronized void OnDisconnect() {
        debug("RemoteN2A disconnected. Removing timers");
        this.hbScanner.stop();
    }

    private synchronized void handleHeartBeatMessage(HeartBeatMessage heartBeatMessage) {
        debug("HB Recieved from %s", getConnectionInfo());
        client.Send(heartBeatMessage);
        this.lastHB = System.currentTimeMillis();
        this.bhMissCount = 0;
        checkRemoteSystemTime(heartBeatMessage.getCreationTime());
    }

    private synchronized void handleHbMissCountReached() {
        warn("Allowed HB misscount reached for %s. Disconnecting client", getConnectionInfo());
        this.hbScanner.stop();
        this.client.closeConnection();
    }

    private void checkRemoteSystemTime(long time) {
        long timeDifference;
        timeDifference = System.currentTimeMillis() - time;

        if (Math.abs(timeDifference) > 300000) {
            warn("Unacceptable time difference detected: %dms. Sending termination request", timeDifference);
            client.Send(new TerminationMessage("Unacceptable time difference detected. Please check your system clock"));
        }
    }

    private synchronized void clockHbScannerFired() {
        if ((System.currentTimeMillis() - this.lastHB) <= this.hbScanner.getIntervalToSeconds()) {
            return;
        }

        this.bhMissCount++;
        warn("HB missed %d/%d from %s", this.bhMissCount, this.allowedHbMisses, getConnectionInfo());

        if (this.bhMissCount >= this.allowedHbMisses) {
            handleHbMissCountReached();
        }
    }

    private String getConnectionInfo() {
        return String.format("%s:%d", client.getRemoteHostAddress(), client.getRemotePort());
    }

}
