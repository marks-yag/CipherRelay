package at.proxy.local

import io.micrometer.core.instrument.MeterRegistry
import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import ketty.core.client.KettyClient
import org.checkerframework.checker.units.qual.C
import java.net.InetSocketAddress

class LocalServerInitializer(key: String, client: KettyClient, connectionManager: ConnectionManager, metrics: Metrics) : ChannelInitializer<SocketChannel>() {

    private val mixInServerHandler = SelectHandler(key, client, connectionManager, metrics)

    @Throws(Exception::class)
    public override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(
            LoggingHandler(LogLevel.DEBUG),
            mixInServerHandler
        )
    }
}