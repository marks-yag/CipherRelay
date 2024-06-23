package at.proxy.protocol

import ketty.core.client.KettyRequestType

enum class AtProxyRequest: KettyRequestType {

    CONNECT,

    DISCONNECT,

    READ,

    WRITE;

    override fun getName(): String {
        return name
    }
}