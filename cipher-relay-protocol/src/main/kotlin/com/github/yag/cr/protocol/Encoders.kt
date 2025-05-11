package com.github.yag.cr.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled

class Encoders {

    companion object {

        @JvmStatic
        fun VirtualChannel.encode(buf: ByteBuf = Unpooled.buffer()): ByteBuf {
            buf.writeLong(id)
            return buf
        }

    }

}