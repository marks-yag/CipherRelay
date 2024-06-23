package at.proxy.local

import at.proxy.local.SocksServerHandler.Companion
import at.proxy.protocol.AtProxyRequest
import at.proxy.protocol.Encoders
import at.proxy.protocol.Encoders.Companion.encode
import at.proxy.protocol.Socks5Connection
import com.github.yag.crypto.AESCrypto
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import ketty.core.client.KettyClient
import ketty.core.common.readArray
import ketty.core.common.use
import org.slf4j.LoggerFactory

class RelayHandler(private val connection: Socks5Connection, private val crypto: AESCrypto, private val kettyClient: KettyClient) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            Unpooled.wrappedBuffer(connection.encode(),
                msg.use {
                    Unpooled.wrappedBuffer(crypto.encrypt(it.readArray()))
                }
            ).use {
                kettyClient.send(
                    AtProxyRequest.WRITE,
                    it
                )
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        log.info("Channel inactive: {}.", connection)
        connection.encode().use {
            kettyClient.send(AtProxyRequest.DISCONNECT, it) {
                log.info("Connection close: {}.", connection)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        super.exceptionCaught(ctx, cause)
        log.warn("Oops! {}.", connection, cause)
    }

    companion object {
        private val log = LoggerFactory.getLogger(RelayHandler::class.java)
    }
}
