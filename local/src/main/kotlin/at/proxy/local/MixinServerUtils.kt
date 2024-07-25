package at.proxy.local

import at.proxy.protocol.AtProxyRequest
import at.proxy.protocol.Encoders.Companion.encode
import at.proxy.protocol.VirtualChannel
import com.github.yag.crypto.AESCrypto
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import ketty.core.client.KettyClient
import ketty.core.common.*
import ketty.core.protocol.ResponseHeader
import ketty.core.protocol.StatusCode
import org.slf4j.LoggerFactory


object MixinServerUtils {

    fun relay(
        client: KettyClient,
        crypto: AESCrypto,
        connect: Packet<ResponseHeader>,
        ctx: ChannelHandlerContext,
        registry: MeterRegistry
    ) {

        val upstreamTraffic: Counter = registry.counter("upstream-traffic")

        val downstreamTraffic: Counter = registry.counter("downstream-traffic")

        val upstreamTrafficEncrypted: Counter = registry.counter("upstream-traffic-encrypted")

        val downstreamTrafficEncrypted: Counter = registry.counter("downstream-traffic-encrypted")
        val vc = VirtualChannel(connect.body.slice().readLong())
        vc.encode().use {
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
        ctx.channel().pipeline().addLast(RelayHandler(vc, crypto, client))
    }

    private val log = LoggerFactory.getLogger(MixinServerUtils::class.java)
}