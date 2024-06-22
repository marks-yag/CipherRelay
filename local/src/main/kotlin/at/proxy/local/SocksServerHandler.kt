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

import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.handler.codec.socksx.SocksMessage
import io.netty.handler.codec.socksx.SocksVersion
import io.netty.handler.codec.socksx.v5.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

@Sharable
class SocksServerHandler(private val atProxyRemoteAddress: InetSocketAddress) : SimpleChannelInboundHandler<SocksMessage>() {
    private val logger: Logger = LoggerFactory.getLogger(SocksServerHandler::class.java)

    private val socketServerConnectHandler = SocksServerConnectHandler(atProxyRemoteAddress)

    @Throws(Exception::class)
    public override fun channelRead0(ctx: ChannelHandlerContext, socksRequest: SocksMessage) {
        if (socksRequest.version() != SocksVersion.SOCKS5) {
            logger.error("only supports socks5 protocol!")
            ctx.writeAndFlush(Unpooled.wrappedBuffer("protocol version illegal!".toByteArray()))
            return
        }
        if (socksRequest is Socks5InitialRequest) {
            ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
            ctx.write(DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH))
            //如果需要密码，这里可以换成
//            ctx.write(new DefaultSocks5InitialResponse(Socks5AuthMethod.PASSWORD));
        } else if (socksRequest is Socks5PasswordAuthRequest) {
            //如果需要密码，这里需要验证密码
            ctx.pipeline().addFirst(Socks5CommandRequestDecoder())
            ctx.write(DefaultSocks5PasswordAuthResponse(Socks5PasswordAuthStatus.SUCCESS))
        } else if (socksRequest is Socks5CommandRequest) {
            if (socksRequest.type() === Socks5CommandType.CONNECT) {
                ctx.pipeline().addLast(socketServerConnectHandler)
                ctx.pipeline().addLast(ConnectionEncoder())
                ctx.pipeline().remove(this)
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

    override fun exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable) {
        logger.error("exceptionCaught", throwable)
        MixinServerUtils.closeOnFlush(ctx.channel())
    }
}
