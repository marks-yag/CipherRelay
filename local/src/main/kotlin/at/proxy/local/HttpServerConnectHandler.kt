/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package at.proxy.local

import at.proxy.protocol.AtProxyRequest
import at.proxy.protocol.Encoders.Companion.encode
import at.proxy.protocol.Connection
import com.github.yag.crypto.AESCrypto
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.ChannelHandler.Sharable
import ketty.core.client.KettyClient
import ketty.core.common.*
import org.slf4j.LoggerFactory

@Sharable
class HttpServerConnectHandler(private val client: KettyClient, private val crypto: AESCrypto) : SimpleChannelInboundHandler<HttpProxyRequestHead>() {

    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, requestHead: HttpProxyRequestHead) {
        val inboundChannel = ctx.channel()
        log.info("New http connection: {} {}. ", requestHead, requestHead.byteBuf.refCnt())
        Unpooled.wrappedBuffer((requestHead.host + ":" + requestHead.port).toByteArray()).use {
            when (requestHead.proxyType) {
                HttpProxyType.TUNNEL -> {
                    client.send(AtProxyRequest.CONNECT, it) { connect ->
                        if (connect.isSuccessful()) {
                            inboundChannel.writeAndFlush(
                                Unpooled.wrappedBuffer(
                                    """${requestHead.protocolVersion} 200 Connection Established

""".toByteArray()
                                )
                            )
                            MixinServerUtils.relay(client, crypto, connect, ctx)
                        } else {
                            ctx.close()
                        }
                    }
                }

                HttpProxyType.WEB -> {
                    val headData = requestHead.byteBuf.retain()
                    client.send(AtProxyRequest.CONNECT, it) { connect ->
                        if (connect.isSuccessful()) {
                            val connection = Connection(connect.body.slice().readLong())
                            MixinServerUtils.relay(client, crypto, connect, ctx)
                            val encrypt = headData.use { crypto.encrypt(it.readArray()) }
                            Unpooled.wrappedBuffer(connection.encode(), Unpooled.wrappedBuffer(encrypt)).use {
                                client.send(AtProxyRequest.WRITE, it) { head ->
                                    if (!head.isSuccessful()) {
                                        ctx.close()
                                    }
                                }
                            }
                        } else {
                            headData.release()
                            ctx.close()
                        }
                    }
                }

                else -> {
                    ctx.close()
                }
            }
        }
    }

    @Throws(Exception::class)
    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        MixinServerUtils.closeOnFlush(ctx.channel())
    }

    companion object {
        private val log = LoggerFactory.getLogger(HttpServerConnectHandler::class.java)


    }
}
