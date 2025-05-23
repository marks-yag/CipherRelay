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

import com.github.yag.crypto.AESCrypto
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksPortUnificationServerHandler
import io.netty.handler.codec.socksx.SocksVersion
import ketty.core.client.KettyClient
import org.slf4j.LoggerFactory

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
