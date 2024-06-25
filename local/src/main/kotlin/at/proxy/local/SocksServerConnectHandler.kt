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

import at.proxy.local.SocksServerHandler.Companion
import at.proxy.protocol.AtProxyRequest
import at.proxy.protocol.Encoders
import at.proxy.protocol.Encoders.Companion.encode
import at.proxy.protocol.Socks5Connection
import com.github.yag.crypto.AESCrypto
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse
import io.netty.handler.codec.socksx.v5.Socks5CommandRequest
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus
import ketty.core.client.client
import ketty.core.common.isSuccessful
import ketty.core.common.readArray
import ketty.core.common.status
import ketty.core.common.use
import ketty.core.protocol.StatusCode
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress

@Sharable
class SocksServerConnectHandler(key: String, atProxyRemoteAddress: InetSocketAddress) : SimpleChannelInboundHandler<SocksMessage>() {
    private val client = client(atProxyRemoteAddress)

    private val crypto = AESCrypto(key.toByteArray())

    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, message: SocksMessage) {
        val request = message as Socks5CommandRequest
        log.info("New socks5 connection: {}:{}.", request.dstAddr(), request.dstPort())
        Unpooled.wrappedBuffer((request.dstAddr() + ":" + request.dstPort()).toByteArray()).use {
            client.sendSync(AtProxyRequest.CONNECT, it).use { connect ->
                if (connect.isSuccessful()) {
                    val connection = Socks5Connection(connect.body.slice().readLong())
                    log.info("Connect to {}:{}, id:{}.", request.dstAddr(), request.dstPort(), connection)
                    ctx.channel().writeAndFlush(
                        DefaultSocks5CommandResponse(
                            Socks5CommandStatus.SUCCESS,
                            request.dstAddrType(),
                            request.dstAddr(),
                            request.dstPort()
                        )
                    )
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
                } else {
                    ctx.channel().writeAndFlush(
                        DefaultSocks5CommandResponse(
                            Socks5CommandStatus.FAILURE, request.dstAddrType()
                        )
                    )
                }
            }
        }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        when (cause) {
            is IOException -> {
                log.debug("IO failed.", cause)
            }

            else -> {
                log.warn("Oops!", cause)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SocksServerConnectHandler::class.java)
    }
}
