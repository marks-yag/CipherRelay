package at.proxy.local

import at.proxy.protocol.AtProxyRequest
import at.proxy.protocol.Encoders.Companion.encode
import at.proxy.protocol.VirtualChannel
import com.github.yag.crypto.AESCrypto
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import ketty.core.client.KettyClient
import ketty.core.common.readArray
import ketty.core.common.use
import org.slf4j.LoggerFactory
import java.io.IOException

class RelayHandler(private val vc: VirtualChannel, private val crypto: AESCrypto, private val kettyClient: KettyClient) : ChannelInboundHandlerAdapter() {

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.writeAndFlush(Unpooled.EMPTY_BUFFER)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        if (msg is ByteBuf) {
            val body = msg.use {
                Unpooled.wrappedBuffer(crypto.encrypt(it.readArray()))
            }
            Unpooled.wrappedBuffer(vc.encode(), body).use {
                kettyClient.send(AtProxyRequest.WRITE, it) {
                    log.debug("Send write request done for {}.", vc)
                }
            }
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        log.debug("Channel inactive: {}.", vc)
        vc.encode().use {
            kettyClient.send(AtProxyRequest.DISCONNECT, it) {
                log.debug("Connection close: {}.", vc)
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        when (cause) {
            is IOException -> {
                log.debug("IO failed {}.", vc)
            }

            else -> {
                log.warn("Oops! {}.", vc, cause)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RelayHandler::class.java)
    }
}
