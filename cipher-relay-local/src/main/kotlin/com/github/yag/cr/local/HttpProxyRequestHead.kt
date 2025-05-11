package at.proxy.local

import io.netty.buffer.ByteBuf

data class HttpProxyRequestHead(
    val host: String,
    val port: Int,
    val proxyType: HttpProxyType,
    val protocolVersion: String,
    val byteBuf: ByteBuf
)
