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
import com.whileloop.nyx2.messages.LoginResponseMessage;
import com.whileloop.nyx2.messages.TerminationMessage;
import com.whileloop.nyx2.utils.NX2IntervalClock;
import com.whileloop.nyx2.utils.NX2Logger;
import com.whileloop.sendit.client.SClient;
import com.whileloop.sendit.messages.SMessage;
import io.netty.channel.nio.NioEventLoopGroup;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author sulochana
 */
public class RemoteN2Agent extends NX2Logger implements NX2IntervalClock.NX2IntervalClockCallback {

    public enum State {

        OFFLINE(0),
        ONLINE(1),
        DISCONNECTED(2);

        public int value;

        private State(int val) {
            this.value = val;
        }
    }

    private static final Map<UUID, RemoteN2Agent> connections = new HashMap<>();
    private static final NioEventLoopGroup agentEventLoopGroup = new NioEventLoopGroup();

    public synchronized static void registerN2A(RemoteN2Agent agent) {
        connections.put(agent.agentUUID, agent);
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
    private LoginResponseMessage.ResponseType registrationOutput;
    private long lastHB = 0x0;
    private int bhMissCount = 0;
    private int agentId = -1;
    private String authToken;

    public RemoteN2Agent(SClient client, int agentId) {
        setVerboseLevel(Loglevel.DEBUG);
        this.client = client;
        this.agentUUID = UUID.randomUUID();
        this.hbScanner = new NX2IntervalClock(agentEventLoopGroup, this, 5, TimeUnit.SECONDS);
        this.client.attachUuid(this.agentUUID);
        this.authToken = generateSecureToken();
        this.agentId = agentId;

        try {
            if (agentId == -1) {
                registerNewOnDatabase();
            } else {
                updateExistingRegistration();
            }
            registerN2A(this);
            this.registrationOutput = LoginResponseMessage.ResponseType.SUCCESS;
        } catch (Exception ex) {
            this.registrationOutput = LoginResponseMessage.ResponseType.INTERNAL_ERROR;
            crit("Failed to register/recognize NX2Agent: %s", ex.getMessage());
        } finally {
            sendLoginResponse();
        }

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
        debug("RemoteN2A disconnected: %s", getConnectionInfo());
        this.hbScanner.stop();
    }

    private synchronized void handleHeartBeatMessage(HeartBeatMessage heartBeatMessage) {
        debug("HB Recieved from %s", getConnectionInfo());
        client.Send(heartBeatMessage);
        this.lastHB = System.currentTimeMillis();
        if (this.bhMissCount > 0) {
            info("Connection restored by %s", getConnectionInfo());
            this.bhMissCount = 0;
        }
        checkRemoteSystemTime(heartBeatMessage.getCreationTime());
    }

    private synchronized void handleHbMissCountReached() {
        warn("Maximum allowed HB miss count reached for %s. Disconnecting agent", getConnectionInfo());
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
        warn("HB missed by %s. [%d/%d]", getConnectionInfo(), this.bhMissCount, this.allowedHbMisses);

        if (this.bhMissCount >= this.allowedHbMisses) {
            handleHbMissCountReached();
        }
    }

    private String getConnectionInfo() {
        return String.format("NX2A-%d [%s:%d]", this.agentId, client.getRemoteHostAddress(), client.getRemotePort());
    }

    private void registerNewOnDatabase() throws SQLException {
        debug("Registering new NX2Agent");
        String query = "INSERT INTO `agents` (`owner_id`, `auth_token`, `state`) VALUES (?, ?, ?);";
        PreparedStatement statement = DBConnection.getConnection().prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
        statement.setInt(1, 21);
        statement.setString(2, this.authToken);
        statement.setInt(3, State.ONLINE.value);
        statement.executeUpdate();
        ResultSet rs = statement.getGeneratedKeys();
        rs.next();
        this.agentId = rs.getInt(1);
        info("Connection from %s:%d registered as NX2A-%d",
                client.getRemoteHostAddress(), client.getRemotePort(), this.agentId);
    }

    private void updateExistingRegistration() throws SQLException {
        debug("Recognizing existing NX2Agent (@%d)", this.agentId);
        String query = "UPDATE `agents` SET `auth_token`= ? WHERE `agent_id`= ?;";
        PreparedStatement statement = DBConnection.getConnection().prepareStatement(query);
        statement.setString(1, this.authToken);
        statement.setInt(2, this.agentId);
        statement.executeUpdate();
        info("Connection from %s:%d recognized as NX2A-%d",
                client.getRemoteHostAddress(), client.getRemotePort(), this.agentId);
    }

    private void sendLoginResponse() {
        LoginResponseMessage msg = new LoginResponseMessage(this.registrationOutput);
        if (this.registrationOutput == LoginResponseMessage.ResponseType.SUCCESS) {
            msg.setAgentUUID(this.agentUUID);
            msg.setAuthToken(this.authToken);
        }
        debug("Sending LoginResponseMessage to %s", getConnectionInfo());
        client.Send(msg);
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[128];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 128);
    }

}
