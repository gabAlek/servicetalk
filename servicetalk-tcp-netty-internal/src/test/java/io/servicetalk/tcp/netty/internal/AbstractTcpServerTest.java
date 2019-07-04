/*
 * Copyright © 2018 Apple Inc. and the ServiceTalk project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.servicetalk.tcp.netty.internal;

import io.servicetalk.buffer.api.Buffer;
import io.servicetalk.concurrent.api.Completable;
import io.servicetalk.concurrent.api.Executors;
import io.servicetalk.concurrent.internal.ServiceTalkTestTimeout;
import io.servicetalk.test.resources.DefaultTestCerts;
import io.servicetalk.transport.api.ConnectionAcceptor;
import io.servicetalk.transport.api.ServerContext;
import io.servicetalk.transport.api.SslConfig;
import io.servicetalk.transport.api.SslConfigBuilders;
import io.servicetalk.transport.api.TestSslConfigBuilders;
import io.servicetalk.transport.netty.IoThreadFactory;
import io.servicetalk.transport.netty.internal.ExecutionContextRule;
import io.servicetalk.transport.netty.internal.NettyConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.Timeout;

import java.net.InetSocketAddress;
import java.util.function.Function;

import static io.servicetalk.buffer.netty.BufferAllocators.DEFAULT_ALLOCATOR;
import static io.servicetalk.transport.api.ConnectionAcceptor.ACCEPT_ALL;
import static io.servicetalk.transport.netty.NettyIoExecutors.createIoExecutor;

public abstract class AbstractTcpServerTest {

    @Rule
    public final Timeout timeout = new ServiceTalkTestTimeout();

    @ClassRule
    public static final ExecutionContextRule SERVER_CTX = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(new IoThreadFactory("server-io-executor")),
            Executors::newCachedThreadExecutor);
    @ClassRule
    public static final ExecutionContextRule CLIENT_CTX = new ExecutionContextRule(() -> DEFAULT_ALLOCATOR,
            () -> createIoExecutor(new IoThreadFactory("client-io-executor")),
            Executors::newCachedThreadExecutor);

    private ConnectionAcceptor connectionAcceptor = ACCEPT_ALL;
    private Function<NettyConnection<Buffer, Buffer>, Completable> service =
            conn -> conn.write(conn.read());
    private boolean sslEnabled;

    protected ServerContext serverContext;
    protected InetSocketAddress serverAddress;
    protected TcpClient client;
    protected TcpServer server;

    void connectionAcceptor(final ConnectionAcceptor connectionAcceptor) {
        this.connectionAcceptor = connectionAcceptor;
    }

    void service(final Function<NettyConnection<Buffer, Buffer>, Completable> service) {
        this.service = service;
    }

    void sslEnabled(final boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    boolean isSslEnabled() {
        return sslEnabled;
    }

    @Before
    public void startServer() throws Exception {
        server = createServer();
        serverContext = server.bind(SERVER_CTX, 0, connectionAcceptor, service, SERVER_CTX.executionStrategy());
        serverAddress = (InetSocketAddress) serverContext.listenAddress();
        client = createClient();
    }

    // Visible for overriding.
    TcpClient createClient() {
        return new TcpClient(getTcpClientConfig());
    }

    // Visible for overriding.
    TcpClientConfig getTcpClientConfig() {
        TcpClientConfig tcpClientConfig = new TcpClientConfig(false);
        if (sslEnabled) {
            final SslConfig sslConfig = TestSslConfigBuilders.forClientWithoutVerificationOrSni()
                    .trustManager(DefaultTestCerts::loadMutualAuthCaPem).build();
            tcpClientConfig = tcpClientConfig.sslConfig(sslConfig);
        }
        return tcpClientConfig;
    }

    // Visible for overriding.
    TcpServer createServer() {
        return new TcpServer(getTcpServerConfig());
    }

    // Visible for overriding.
    TcpServerConfig getTcpServerConfig() {
        TcpServerConfig tcpServerConfig = new TcpServerConfig(false);
        if (sslEnabled) {
            final SslConfig sslConfig = SslConfigBuilders.forServer(
                    DefaultTestCerts::loadServerPem,
                    DefaultTestCerts::loadServerKey)
                    .build();
            tcpServerConfig = tcpServerConfig.sslConfig(sslConfig);
        }
        return tcpServerConfig;
    }

    @After
    public void stopServer() throws Exception {
        serverContext.closeAsync().toFuture().get();
    }
}
