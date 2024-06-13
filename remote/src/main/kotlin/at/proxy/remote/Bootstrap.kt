package at.proxy.remote

import at.proxy.protocol.SocketWithStream
import at.proxy.protocol.Type
import at.proxy.protocol.Utils
import java.io.DataInputStream
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

object Bootstrap {

    @JvmStatic
    fun main(args: Array<String>) {
        val executor = Executors.newCachedThreadPool()
        val server = ServerSocket(9528)
        while (true) {
            val client = server.accept()
            val inbounds = SocketWithStream("from-local", client,
                Utils.cipherInputStream(client),
                Utils.cipherOutputStream(client)
            )
            executor.execute {
                val din = DataInputStream(inbounds.ins)
                val id = din.readUTF()
                val address = din.readUTF()
                val port = din.readInt()
                println("Relay: $address:$port")

                val relay = Socket(address, port)
                relay.soTimeout = 30 * 1000
                val outbounds = SocketWithStream("to-site", relay, relay.getInputStream(), relay.getOutputStream())
                Utils.doRelay(id, Type.RIN, outbounds, inbounds)
                Utils.doRelay(id, Type.ROUT, inbounds, outbounds)
            }
        }
    }


}