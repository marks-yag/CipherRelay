package com.github.yag.cr.protocol

import ketty.core.client.KettyRequestType

enum class AtProxyRequest: KettyRequestType {

    CONNECT,

    DISCONNECT,

    READ,

    WRITE,
    
    STATUS;

    override fun getName(): String {
        return name
    }
}