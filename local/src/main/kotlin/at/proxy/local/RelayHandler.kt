package at.proxy.local

import at.proxy.protocol.AtProxyRequest
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.CompositeByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.ReferenceCountUtil
import ketty.core.client.KettyClient
import ketty.core.client.KettyRequestType
import org.slf4j.LoggerFactory

class RelayHandler(private val connection: Socks5Connection, private val kettyClient: KettyClient) : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        kettyClient.send(AtProxyRequest.WRITE, Unpooled.wrappedBuffer(ConnectionEncoder.encode(connection, Unpooled.buffer()), (msg as ByteBuf).retain()))
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {

    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        log.warn("Exception caught: {}.", connection, cause)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RelayHandler::class.java)
    }
}
