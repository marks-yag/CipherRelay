package at.proxy.remote

import at.proxy.protocol.Utils
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

object Bootstrap {

    @JvmStatic
    fun main(args: Array<String>) {
        val executor = Executors.newCachedThreadPool()
        val server = ServerSocket(9528)
        while (true) {
            val client = server.accept()
            executor.execute {
                val ins = client.getInputStream()
                val out = client.getOutputStream()

                val din = DataInputStream(ins)
                val address = din.readUTF()
                val port = din.readInt()

                val relay = Socket(address, port)
                doRelay(client, relay)
                doRelay(relay, client)
            }
        }
    }

    fun doRelay(from: Socket, to: Socket) =
        thread {
            try {
                from.getInputStream().copyTo(to.getOutputStream(), 8192)
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