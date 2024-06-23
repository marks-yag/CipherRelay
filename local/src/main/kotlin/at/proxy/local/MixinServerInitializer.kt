package at.proxy.local

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import java.net.InetSocketAddress

class MixinServerInitializer(atProxyRemoteAddress: InetSocketAddress) : ChannelInitializer<SocketChannel>() {

    private val mixInServerHandler = SelectHandler(atProxyRemoteAddress)

    @Throws(Exception::class)
    public override fun initChannel(ch: SocketChannel) {
        ch.pipeline().addLast(
            LoggingHandler(LogLevel.DEBUG),
            mixInServerHandler
        )
    }
}