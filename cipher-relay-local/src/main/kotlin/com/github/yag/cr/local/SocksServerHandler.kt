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
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.*
import ketty.core.client.KettyClient
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

@Sharable
class SocksServerHandler(private val connectionManager: ConnectionManager, client: KettyClient, crypto: AESCrypto, metrics: Metrics) : SimpleChannelInboundHandler<SocksMessage>() {

    private val socketServerConnectHandler = SocksServerConnectHandler(connectionManager, client, crypto, metrics)

    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, socksRequest: SocksMessage) {
        if (socksRequest.version() != SocksVersion.SOCKS5) {
            ctx.writeAndFlush(Unpooled.wrappedBuffer("Unsupported protocol version.".toByteArray()))
            return
        }
        if (socksRequest is Socks5InitialRequest) {
            ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
            ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
        } else if (socksRequest is Socks5PasswordAuthRequest) {
            ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
            ctx.write(DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
        } else if (socksRequest is Socks5CommandRequest) {
            if (socksRequest.type() === Socks5CommandType.CONNECT) {
                ctx.pipeline().addLast(socketServerConnectHandler)
                ctx.pipeline().remove(this)
                connectionManager.addConnection(
                    ctx.channel().id(),
                    Socks5Connection(ctx.channel().remoteAddress() as InetSocketAddress, socksRequest.dstAddr(), socksRequest.dstPort(), connectionManager)
                )
                ctx.fireChannelRead(socksRequest)
            } else {
                ctx.close()
            }
        } else {
            ctx.close()
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.flush()
    }

    companion object {
        private val log = LoggerFactory.getLogger(SocksServerHandler::class.java)
    }
}
