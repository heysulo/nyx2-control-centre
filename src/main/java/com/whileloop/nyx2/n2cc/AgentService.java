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

import com.whileloop.nyx2.messages.LoginMessage;
import com.whileloop.nyx2.messages.LoginResponseMessage;
import com.whileloop.nyx2.messages.ServerStatusMessage;
import com.whileloop.nyx2.utils.NX2Logger;
import com.whileloop.sendit.callbacks.SServerCallback;
import com.whileloop.sendit.client.SClient;
import com.whileloop.sendit.messages.SMessage;
import com.whileloop.sendit.server.SServer;
import io.netty.channel.nio.NioEventLoopGroup;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.util.Base64;
import javax.net.ssl.SSLException;

/**
 *
 * @author sulochana
 */
public class AgentService extends NX2Logger implements SServerCallback {

    private final NioEventLoopGroup bossGroup;
    private final NioEventLoopGroup workerGroup;
    private final static AgentService instance = new AgentService();
    private SServer agentService;
    private final int serverPort;
    private boolean serverReady;

    public AgentService() {
        this.serverReady = false;
        this.setVerboseLevel(Loglevel.INFO);
        this.bossGroup = new NioEventLoopGroup();
        this.workerGroup = new NioEventLoopGroup();
        this.serverPort = 3000;

        try {
            startService();
            readyServer();
        } catch (Exception ex) {
            crit("Failed to start agent service on port %d: %s", this.serverPort, ex.getMessage());
            shutdownService();
        }
    }

    public final void readyServer() {
        debug("Putting Agent Service into Ready state");
        this.serverReady = true;
        info("---------- Agent Service READY ----------");
    }

    public boolean isServerReady() {
        return serverReady;
    }

    public final void shutdownService() {
        debug("Shutting down Agent Service");
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
        debug("Agent Service shutdown complete");
    }

    private void startService() throws CertificateException, SSLException, InterruptedException {
        debug("Attempting to start Agent Service on port: %d", this.serverPort);
        agentService = new SServer(this.serverPort, this.workerGroup, this.bossGroup, this);
        info("Agent Service started on port: %d", this.serverPort);
    }

    public static AgentService getInstance() {
        return instance;
    }

    @Override
    public void OnConnect(SServer server, SClient client) {
        debug("Client connection from %s", getClientConnectionInfo(client));
    }

    @Override
    public void OnDisconnect(SServer server, SClient client) {
        debug("Client disconnected %s", getClientConnectionInfo(client));
        RemoteN2Agent agent = RemoteN2Agent.findAgent(client.getAttachedUuid());
        if (agent == null) {
            debug("Ignoring disconnection of unknown client %s", getClientConnectionInfo(client));
            return;
        }

        agent.OnDisconnect();
        RemoteN2Agent.deregisterAgent(client.getAttachedUuid());
    }

    @Override
    public void OnMessage(SServer server, SClient client, SMessage msg) {
        debug("OnMessage");
        if (msg instanceof LoginMessage) {
            handleLoginMessage(client, (LoginMessage) msg);
        }

        RemoteN2Agent agent = RemoteN2Agent.findAgent(client.getAttachedUuid());
        if (agent == null) {
            debug("Ignoring %s from unknown client %s", msg.getClass().getName(), getClientConnectionInfo(client));
            return;
        }

        agent.OnRemoteMessage(msg);
    }

    @Override
    public void OnError(SServer server, SClient client, Throwable cause) {
        debug("OnError");
    }

    @Override
    public void OnEvent(SServer server, SClient client, Object event) {
        debug("OnEvent");
    }

    @Override
    public void OnSSLHandshakeSuccess(SServer server, SClient client) {
        debug("Secure Connection established with client %s using CS: {@%s} PT: {@%s}",
                getClientConnectionInfo(client),
                client.getCipherSuite(), client.getProtocol());
        debug("Sending ServerStatusMessage to %s", getClientConnectionInfo(client));
        client.Send(new ServerStatusMessage(this.isServerReady()));
    }

    @Override
    public void OnSSLHandshakeFailure(SServer server, SClient client) {
        debug("OnSSLHandshakeFailure");
    }

    private String getClientConnectionInfo(SClient client) {
        return String.format("%s:%d", client.getRemoteHostAddress(), client.getRemotePort());
    }

    private void handleLoginMessage(SClient client, LoginMessage msg) {
        debug("LoginMessage recieved from: %s. Mechanism: %s",
                getClientConnectionInfo(client), msg.getMechanism().toString());
        // TODO: Validate credentials

        LoginResponseMessage respMsg = new LoginResponseMessage(true);
        respMsg.setAuthToken(generateSecureToken());
        debug("Sending LoginResponseMessage to client %s", getClientConnectionInfo(client));
        client.Send(respMsg);
        RemoteN2Agent remoteClient = new RemoteN2Agent(client, respMsg.getAgentUUID());
    }

    private String generateSecureToken() {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[128];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
