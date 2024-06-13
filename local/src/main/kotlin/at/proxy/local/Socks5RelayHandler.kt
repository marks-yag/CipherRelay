package at.proxy.local

import at.proxy.protocol.SocketWithStream
import at.proxy.protocol.Type
import at.proxy.protocol.Utils
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

class Socks5RelayHandler(private val remoteAddr: InetSocketAddress) {
    fun doRelay(client: Socket, targetAddr: InetSocketAddress) {
        val remote = Socket(remoteAddr.hostName, remoteAddr.port)
        remote.soTimeout = 30 * 1000
        val inbounds = SocketWithStream("from-client", client, client.getInputStream(), client.getOutputStream())
        val outbounds = SocketWithStream(
            "to-remote",
            remote,
            Utils.cipherInputStream(remote),
            Utils.cipherOutputStream(remote))

        val id = UUID.randomUUID().toString()
        DataOutputStream(outbounds.os).apply {
            writeUTF(id)
            writeUTF(targetAddr.hostName)
            writeInt(targetAddr.port)
            flush()
        }

        val clientToRemote = Utils.doRelay(id, Type.OUT, inbounds, outbounds)
        val remoteToClient = Utils.doRelay(id, Type.IN, outbounds, inbounds)
    }


}
