package at.proxy.local

import com.github.yag.crypto.AESCrypto
import io.micrometer.core.instrument.MeterRegistry
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.codec.socksx.SocksVersion
import ketty.core.client.KettyClient
import ketty.core.client.client
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.nio.charset.Charset

@Sharable
class SelectHandler(key: String, private val client: KettyClient, private val connectionManager: ConnectionManager, private val metrics: Metrics) : SimpleChannelInboundHandler<ByteBuf>() {

    private val crypto = AESCrypto(key.toByteArray())

    private val socksServerHandler = SocksServerHandler(connectionManager, client, crypto, metrics)

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (msg.readableBytes() == 0) return
        val p = ctx.pipeline()
        val versionVal = msg.slice().readByte()
        val version = SocksVersion.valueOf(versionVal)
        if (version == SocksVersion.SOCKS4a || version == SocksVersion.SOCKS5) {
            p.addLast(SocksPortUnificationServerHandler(), socksServerHandler)
        } else {
            p.addLast(HttpServerHeadDecoder(connectionManager, client, crypto, metrics))
        }
        p.remove(this)
        ctx.fireChannelRead(msg.retain())
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SelectHandler::class.java)
    }
}
