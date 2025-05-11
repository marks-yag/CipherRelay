package com.github.yag.cr.local

import com.github.yag.cr.protocol.AtProxyRequest
import com.github.yag.cr.protocol.Encoders.Companion.encode
import com.github.yag.cr.protocol.VirtualChannel
import com.github.yag.crypto.AESCrypto
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import ketty.core.client.KettyClient
import ketty.core.common.*
import ketty.core.protocol.ResponseHeader
import ketty.core.protocol.StatusCode
import org.slf4j.LoggerFactory


object MixinServerUtils {

    fun relay(
        connection: Connection,
        client: KettyClient,
        crypto: AESCrypto,
        connect: Packet<ResponseHeader>,
        ctx: ChannelHandlerContext,
        metrics: Metrics
    ) {
        val vc = VirtualChannel(connect.body.slice().readLong())
        vc.encode().use {
            client.send(AtProxyRequest.READ, it) { response ->
                connection.increaseDownloadTrafficInBytes(response.length().toLong())
                metrics.downstreamTrafficEncrypted.increment(response.length().toDouble())
                if (response.isSuccessful()) {
                    log.debug("Received read response, length: {}.", response.body.readableBytes())
                    if (response.status() == StatusCode.PARTIAL_CONTENT) {
                        val decrypt = crypto.decrypt(response.body.readArray())
                        metrics.downstreamTraffic.increment(decrypt.size.toDouble())
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
        ctx.channel().pipeline().addLast(RelayHandler(connection, vc, crypto, client, metrics))
    }

    private val log = LoggerFactory.getLogger(MixinServerUtils::class.java)
}