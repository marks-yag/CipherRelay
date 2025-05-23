/*
 * Copyright 2024-2025 marks.yag@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.yag.cr.local

import com.google.common.net.HostAndPort
import config.config
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import ketty.core.client.KettyClient
import ketty.core.client.client
import ketty.utils.MemoryLeakDetector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

class LocalServer(config: LocalConfig) : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(LocalServer::class.java)

    private var serverBootstrap: ServerBootstrap

    private var serverEventLoopGroup: EventLoopGroup

    private var acceptorChannel: Channel
    
    private val client: KettyClient

    val connectionManager = ConnectionManager()

    val metrics = Metrics()

    init {
        logger.info("Proxy Server starting...")
        serverEventLoopGroup = NioEventLoopGroup(Runtime.getRuntime().availableProcessors())
        val atProxyRemoteAddress =
            HostAndPort.fromString(config.remoteEndpoint).let { InetSocketAddress(it.host, it.port) }
        client = client(atProxyRemoteAddress)
        serverBootstrap = ServerBootstrap()
            .channel(NioServerSocketChannel::class.java)
            .childHandler(LocalServerInitializer(
                config.key,
                client,
                connectionManager,
                metrics)
            ).group(serverEventLoopGroup)
        acceptorChannel = serverBootstrap.bind(config.port).syncUninterruptibly().channel()
    }
    
    fun getEndpoint() = acceptorChannel.localAddress() as InetSocketAddress

    override fun close() {
        logger.info("Proxy Server shutting down...")
        acceptorChannel.close().syncUninterruptibly()
        serverEventLoopGroup.shutdownGracefully().syncUninterruptibly()
        client.close()
        logger.info("shutdown completed!")
    }

    companion object {

        private val LOG = LoggerFactory.getLogger(LocalServer::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val config = System.getProperties().config(LocalConfig::class.java)
            val server = LocalServer(config)
            Runtime.getRuntime().addShutdownHook(Thread {
                server.close()
            })
            val leakCheckInterval = System.getProperty("leakCheckInterval", "-1").toLong()
            if (leakCheckInterval > 0) {
                val detector = MemoryLeakDetector()
                while (detector.count() == 0L) {
                    Thread.sleep(leakCheckInterval)
                }
            }
            Thread.sleep(Long.MAX_VALUE)
        }
    }
}
