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

package com.github.yag.cr.remote

import com.github.yag.cr.protocol.AtProxyRequest
import com.github.yag.crypto.AESCrypto
import config.config
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.DefaultThreadFactory
import ketty.core.common.*
import ketty.core.protocol.StatusCode
import ketty.core.server.*
import ketty.utils.MemoryLeakDetector
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class RemoteServer(config: RemoteConfig) : AutoCloseable {

    private val server: KettyServer

    private val crypto = AESCrypto(config.key.toByteArray())

    private val eventloop = createEventLoopGroup(Runtime.getRuntime().availableProcessors(), "remote")

    init {
        server = server {
            config {
                port = config.port
                host = "0.0.0.0"
            }
            connection {
                add(object: KettyConnectionHandler {
                    private val channels = ConcurrentHashMap<Long, ChannelFuture>()

                    override fun handle(connection: KettyConnection) {
                        connection.put("current_connection_id", AtomicLong())
                        connection.put("connections", channels);
                    }

                    override fun inactive(connection: KettyConnection) {
                        channels.values.forEach {
                            it.channel().close()
                        }
                    }
                })
            }
            @Suppress("UNCHECKED_CAST")
            request {
                set(AtProxyRequest.CONNECT) { connection, request, echo ->
                    val endpoint = request.body.toString(Charsets.UTF_8)
                    log.info("Received connect request, endpoint: {}.", endpoint)
                    val host = endpoint.split(":").first()
                    val port = endpoint.split(":").last().toInt()
                    val targetSiteBootstrap = Bootstrap()
                    val connectionId = (connection.get("current_connection_id") as AtomicLong).getAndIncrement()
                    val c = targetSiteBootstrap.group(eventloop)
                        .channel(getChannelClass())
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20_000)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .handler(object : ChannelInboundHandlerAdapter() {
                            override fun channelActive(ctx: ChannelHandlerContext) {
                                connection.put("ctx-$connectionId", ctx)
                            }

                            override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                                if (msg is ByteBuf) {
                                    val echo = connection.get("echo-$connectionId") as (ResponsePacket) -> Unit
                                    val connectionRequest = connection.get("request-$connectionId") as (RequestPacket)
                                    val encrypt = Unpooled.wrappedBuffer(crypto.encrypt(msg.readArray()))
                                    echo.invoke(connectionRequest.status(StatusCode.PARTIAL_CONTENT, encrypt))
                                }
                                ReferenceCountUtil.release(msg)
                            }

                            override fun channelInactive(ctx: ChannelHandlerContext) {
                                val echo = connection.get("echo-$connectionId") as (ResponsePacket) -> Unit
                                val connectionRequest = connection.get("request-$connectionId") as (RequestPacket)
                                echo(connectionRequest.status(StatusCode.OK))
                            }

                            override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                                when (cause) {
                                    is IOException -> {
                                        log.debug("IO failed {}.", connection)
                                    }

                                    else -> {
                                        log.warn("Oops! {}.", connection, cause)
                                    }
                                }
                            }
                        })
                        .connect(host, port)
                        .addListener(ChannelFutureListener { future ->
                            if (future.isSuccess) {
                                log.info("Connected to {}:{}", host, port)
                                echo(request.ok(Unpooled.buffer().writeLong(connectionId)))
                            } else {
                                echo(request.status(StatusCode.INTERNAL_ERROR))
                            }
                        })
                    (connection.get("connections") as ConcurrentHashMap<Long, ChannelFuture>)[connectionId] = c
                }
                set(AtProxyRequest.DISCONNECT) { connection, request, echo ->
                    val connectionId = request.body.readLong()
                    log.info("Disconnect: {}.", connectionId)
                    (connection.get("connections") as ConcurrentHashMap<Long, ChannelFuture>).remove(connectionId)
                        ?.channel()?.close()
                }
                set(AtProxyRequest.WRITE) { connection, request, echo ->
                    val connectionId = request.body.readLong()
                    log.debug("Received write request, connectionId: {}", connectionId)
                    val ctx = connection.get("ctx-$connectionId") as ChannelHandlerContext
                    ctx.writeAndFlush(Unpooled.wrappedBuffer(crypto.decrypt(request.body.readArray())))
                    echo(request.ok())
                }
                set(AtProxyRequest.READ) { connection, request, echo ->
                    val connectionId = request.body.readLong()
                    log.debug("Received read request, connectionId: {}", connectionId)
                    connection.put("echo-$connectionId", echo)
                    connection.put("request-$connectionId", request)
                }
                set(AtProxyRequest.STATUS) { connection, request, echo ->
                    echo(request.ok("hello"))
                }
            }
        }
    }

    override fun close() {
        server.close()
        eventloop.close()
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val config = System.getProperties().config(RemoteConfig::class.java)
            val server = RemoteServer(config)
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

        internal fun createEventLoopGroup(threads: Int, name: String): EventLoopGroup {
            val threadFactory = DefaultThreadFactory(name, true)
            return when {
                Epoll.isAvailable() -> {
                    EpollEventLoopGroup(threads, threadFactory)
                }

                KQueue.isAvailable() -> {
                    KQueueEventLoopGroup(threads, threadFactory)
                }

                else -> {
                    NioEventLoopGroup(threads, threadFactory)
                }
            }
        }

        fun getChannelClass(): Class<out Channel> {
            return when {
                Epoll.isAvailable() -> {
                    EpollSocketChannel::class.java
                }

                KQueue.isAvailable() -> {
                    KQueueSocketChannel::class.java
                }

                else -> {
                    NioSocketChannel::class.java
                }
            }
        }

        private val log = LoggerFactory.getLogger(RemoteServer::class.java)
    }
}