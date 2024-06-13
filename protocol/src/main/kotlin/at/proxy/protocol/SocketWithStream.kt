package at.proxy.protocol

import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

data class SocketWithStream(val name: String, val socket: Socket, val ins: InputStream, val os: OutputStream) {
    override fun toString(): String {
        return name
    }
}
