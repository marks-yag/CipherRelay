/*
 * Copyright 2024-2025 marks.yag@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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