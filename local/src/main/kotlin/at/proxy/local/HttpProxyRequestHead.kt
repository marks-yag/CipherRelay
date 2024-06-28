package at.proxy.local

import io.netty.buffer.ByteBuf

data class HttpProxyRequestHead(
    val host: String,
    val port: Int,
    val proxyType: String,
    val protocolVersion: String,
    val byteBuf: ByteBuf
)
