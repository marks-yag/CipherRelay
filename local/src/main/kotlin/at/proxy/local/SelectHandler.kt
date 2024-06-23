package at.proxy.local

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.codec.socksx.SocksVersion
import java.net.InetSocketAddress

@Sharable
class SelectHandler(atProxyRemoteAddress: InetSocketAddress) : SimpleChannelInboundHandler<ByteBuf>() {
    private val socksServerHandler = SocksServerHandler(atProxyRemoteAddress)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val readerIndex = msg.readerIndex()
        if (msg.writerIndex() == readerIndex) {
            return
        }

        val p = ctx.pipeline()
        val versionVal = msg.getByte(readerIndex)

        val version = SocksVersion.valueOf(versionVal)
        if (version == SocksVersion.SOCKS4a || version == SocksVersion.SOCKS5) {
            //socks proxy
            p.addLast(
                SocksPortUnificationServerHandler(),
                socksServerHandler
            ).remove(this)
        } else {
            //http/tunnel proxy
            //p.addLast(HttpServerHeadDecoder()).remove(this)
        }
        msg.retain()
        ctx.fireChannelRead(msg)
    }
}
