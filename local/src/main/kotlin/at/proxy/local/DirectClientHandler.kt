package at.proxy.local

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.concurrent.Promise

class DirectClientHandler(private val promise: Promise<Channel>) : ChannelInboundHandlerAdapter() {
    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.pipeline().remove(this)
        promise.setSuccess(ctx.channel())
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable) {
        promise.setFailure(throwable)
    }
}