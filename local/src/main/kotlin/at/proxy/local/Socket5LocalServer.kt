/*
 * Copyright 2018-2020 marks.yag@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package at.proxy.local

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.channel.*
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.epoll.EpollServerDomainSocketChannel
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueEventLoopGroup
import io.netty.channel.kqueue.KQueueServerDomainSocketChannel
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.unix.DomainSocketAddress
import io.netty.channel.unix.DomainSocketChannel
import io.netty.channel.unix.ServerDomainSocketChannel
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.LengthFieldBasedFrameDecoder
import io.netty.handler.codec.LengthFieldPrepender
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.util.concurrent.DefaultThreadFactory
import ketty.core.common.*
import ketty.core.protocol.*
import ketty.core.server.KettyEndpoint
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.*
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class Socket5LocalServer internal constructor(
    private val config: Socks5ServerConfig
) : AutoCloseable {

    val endpoint: KettyEndpoint<InetSocketAddress>

    internal val serverBootstrap: ServerBootstrap

    private val handler = ChildChannelHandler()

    private val closed = AtomicBoolean()

    private val type = getType()

    init {
        serverBootstrap = createServerBootstrap()
        endpoint = createEndpoint(serverBootstrap, "0.0.0.0")
    }

    private fun getType() = when {
        Epoll.isAvailable() -> {
            Type.EPOLL
        }
        KQueue.isAvailable() -> {
            Type.KQUEUE
        }
        else -> {
            Type.NIO
        }
    }

    private fun createParentGroup() : EventLoopGroup {
        return createEventLoopGroup(config.parentThreads,"parent")
    }

    private fun createChildGroup() : EventLoopGroup {
        return createEventLoopGroup(config.childThreads,"child")
    }

    private fun createEventLoopGroup(threads: Int, name: String) : EventLoopGroup {
        val threadFactory = DefaultThreadFactory("server-$name", true)
        return when (type) {
            Type.EPOLL -> EpollEventLoopGroup(threads, threadFactory)
            Type.KQUEUE -> KQueueEventLoopGroup(threads, threadFactory)
            Type.NIO -> NioEventLoopGroup(threads, threadFactory)
        }
    }

    private fun getServerSocketChannel() : Class<out ServerSocketChannel> {
        return when(type) {
            Type.EPOLL -> EpollServerSocketChannel::class.java
            Type.KQUEUE -> KQueueServerSocketChannel::class.java
            Type.NIO -> NioServerSocketChannel::class.java
        }
    }

    private fun getDomainSocketChannel() : Class<out ServerDomainSocketChannel> {
        return when(type) {
            Type.EPOLL -> EpollServerDomainSocketChannel::class.java
            Type.KQUEUE -> KQueueServerDomainSocketChannel::class.java
            Type.NIO -> throw UnsupportedOperationException("Domain socket is unsupported.")
        }
    }

    private fun createEndpoint(
        serverBootstrap: ServerBootstrap,
        host: String
    ) = try {
        val channelFuture = serverBootstrap.bind(host, config.port).sync()
        val endpoint = channelFuture.channel().localAddress() as InetSocketAddress
        LOG.info("Ketty server started on: {}.", endpoint)
        KettyEndpoint(serverBootstrap, channelFuture, endpoint)
    } catch (e: BindException) {
        LOG.error("Port conflict: {}.", config.port, e)
        throw e
    }

    private fun createServerBootstrap(): ServerBootstrap {
        val serverBootstrap = ServerBootstrap().apply {
            channel(getServerSocketChannel())
                .group(createParentGroup(), createChildGroup())
        }
        return serverBootstrap
    }

    inner class ChildChannelHandler : ChannelInitializer<Channel>() {

        override fun initChannel(socketChannel: Channel) {
            LOG.debug("New tcp connection arrived: {}.", socketChannel.id())
            socketChannel.pipeline().apply {


            }
        }

    }


    override fun close() {
        if (closed.compareAndSet(false, true)) {
            LOG.info("Closing ketty server.")
            LOG.debug("Request handler closed.")
            endpoint.close()
            serverBootstrap.config().group().shutdownGracefully()
            serverBootstrap.config().childGroup().shutdownGracefully()
            LOG.info("Ketty server closed.")
        }
    }

    enum class Type {
        EPOLL,
        KQUEUE,
        NIO
    }

    companion object {
        private val LOG =  LoggerFactory.getLogger(Socket5LocalServer::class.java)
    }

}
