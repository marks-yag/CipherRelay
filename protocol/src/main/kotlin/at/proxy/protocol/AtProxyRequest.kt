package at.proxy.protocol

import ketty.core.client.KettyRequestType

enum class AtProxyRequest: KettyRequestType {

    CONNECT,

    READ,

    WRITE;

    override fun getName(): String {
        return name
    }
}