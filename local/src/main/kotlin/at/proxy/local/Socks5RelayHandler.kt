package at.proxy.local

import at.proxy.protocol.Utils
import java.io.DataOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.crypto.CipherInputStream
import kotlin.concurrent.thread

class Socks5RelayHandler(private val remoteAddr: InetSocketAddress) {
    fun doRelay(client: Socket, targetAddr: InetSocketAddress) {
        val remote = Socket(remoteAddr.hostName, remoteAddr.port)
        remote.soTimeout = 30 * 1000
        val input = client.getInputStream()
        val output = remote.getOutputStream()
        DataOutputStream(output).apply {
            writeUTF(targetAddr.hostName)
            writeInt(targetAddr.port)
        }

        val clientToRemote = doRelay(client, remote)
        val remoteToClient = doRelay(remote, client)
    }

    fun doRelay(from: Socket, to: Socket) =
        thread {
            try {
                val output = to.getOutputStream()
                from.getInputStream().copyTo(output, 8192)
            } finally {
                if (!from.isClosed) {
                    from.close()
                }
                if (!to.isClosed) {
                    to.shutdownInput()
                }
            }
        }

}
