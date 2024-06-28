package at.proxy.local

import com.github.yag.crypto.AESCrypto
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.codec.socksx.SocksVersion
import ketty.core.client.client
import java.net.InetSocketAddress

@Sharable
class SelectHandler(key: String, atProxyRemoteAddress: InetSocketAddress) : SimpleChannelInboundHandler<ByteBuf>() {

    private val client = client(atProxyRemoteAddress)

    private val crypto = AESCrypto(key.toByteArray())

    private val socksServerHandler = SocksServerHandler(client, crypto)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val readerIndex = msg.readerIndex()
        if (msg.writerIndex() == readerIndex) {
            return
        }

        val p = ctx.pipeline()
        val versionVal = msg.getByte(readerIndex)

        val version = SocksVersion.valueOf(versionVal)
        if (version == SocksVersion.SOCKS4a || version == SocksVersion.SOCKS5) {
            p.addLast(
                SocksPortUnificationServerHandler(),
                socksServerHandler
            ).remove(this)
        } else {
            //http/tunnel proxy
            p.addLast(HttpServerHeadDecoder(client, crypto)).remove(this)
        }
        msg.retain()
        ctx.fireChannelRead(msg)
    }
}
