package at.proxy.local

import com.google.common.net.HostAndPort
import config.config
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import ketty.utils.MemoryLeakDetector
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress


class LocalServer(config: LocalConfig) : AutoCloseable {
    private val logger: Logger = LoggerFactory.getLogger(LocalServer::class.java)

    private var serverBootstrap: ServerBootstrap

    private var serverEventLoopGroup: EventLoopGroup

    private var acceptorChannel: Channel

    val metrics = Metrics()

    init {
        logger.info("Proxy Server starting...")
        serverEventLoopGroup = NioEventLoopGroup(Runtime.getRuntime().availableProcessors())

        serverBootstrap = ServerBootstrap()
            .channel(NioServerSocketChannel::class.java)
            .childHandler(LocalServerInitializer(
                config.key,
                HostAndPort.fromString(config.remoteEndpoint).let { InetSocketAddress(it.host, it.port) },
                metrics)
            ).group(serverEventLoopGroup)
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
