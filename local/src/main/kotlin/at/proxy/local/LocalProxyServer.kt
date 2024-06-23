package at.proxy.local

import config.config
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class LocalProxyServer(private val config: Socks5ServerConfig) : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(LocalProxyServer::class.java)

    private var serverBootstrap: ServerBootstrap

    private var serverEventLoopGroup: EventLoopGroup

    private var acceptorChannel: Channel

    init {
        logger.info("Proxy Server starting...")

        serverEventLoopGroup = NioEventLoopGroup(4)

        serverBootstrap = ServerBootstrap()
            .channel(NioServerSocketChannel::class.java)
            .childHandler(MixinServerInitializer(config.remoteEndpoint))
            .group(serverEventLoopGroup)
        acceptorChannel = serverBootstrap.bind(config.port).syncUninterruptibly().channel()
    }

    override fun close() {
        logger.info("Proxy Server shutting down...")
        acceptorChannel.close().syncUninterruptibly()
        serverEventLoopGroup.shutdownGracefully().syncUninterruptibly()
        logger.info("shutdown completed!")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val config = System.getProperties().config(Socks5ServerConfig::class.java)
            val server = LocalProxyServer(config)
            Runtime.getRuntime().addShutdownHook(Thread {
                server.close()
            })
            System.`in`.read()
        }
    }
}
