package at.proxy.local

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

internal class ConnectionEncoder: MessageToByteEncoder<Socks5Connection>(Socks5Connection::class.java) {

    override fun encode(ctx: ChannelHandlerContext, msg: Socks5Connection, out: ByteBuf) {
        encode(msg, out)
    }

    companion object {

        @JvmStatic
        fun  encode(connection: Socks5Connection, buf: ByteBuf = Unpooled.buffer()): ByteBuf {
            buf.writeLong(connection.id)
            return buf
        }

    }

}