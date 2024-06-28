package at.proxy.local

import at.proxy.protocol.AtProxyRequest
import at.proxy.protocol.Encoders.Companion.encode
import at.proxy.protocol.Connection
import com.github.yag.crypto.AESCrypto
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelFutureListener
import io.netty.channel.ChannelHandlerContext
import ketty.core.client.KettyClient
import ketty.core.common.*
import ketty.core.protocol.ResponseHeader
import ketty.core.protocol.StatusCode
import org.slf4j.LoggerFactory


object MixinServerUtils {
    /**
     * Closes the specified channel after all queued write requests are flushed.
     */
    fun closeOnFlush(ch: Channel) {
        if (ch.isActive) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
        }
    }

    fun relay(
        client: KettyClient,
        crypto: AESCrypto,
        connect: Packet<ResponseHeader>,
        ctx: ChannelHandlerContext
    ) {
        val connection = Connection(connect.body.slice().readLong())
        connection.encode().use {
            client.send(AtProxyRequest.READ, it) { response ->
                if (response.isSuccessful()) {
                    log.debug("Received read response, length: {}.", response.body.readableBytes())
                    if (response.status() == StatusCode.PARTIAL_CONTENT) {
                        val decrypt = crypto.decrypt(response.body.readArray())
                        ctx.channel().writeAndFlush(Unpooled.wrappedBuffer(decrypt))
                    } else {
                        ctx.channel().close()
                    }
                } else {
                    log.warn("Read failed.")
                    ctx.channel().close()
                }
            }
        }
        ctx.channel().pipeline().addLast(RelayHandler(connection, crypto, client))
    }

    private val log = LoggerFactory.getLogger(MixinServerUtils::class.java)
}