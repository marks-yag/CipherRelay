package at.proxy.local

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.BlockingQueue

class Socks5Acceptor(private val port: Int, remoteAddr: InetSocketAddress) : Runnable {

    private val handler = Socks5Handler(remoteAddr)

    override fun run() {
        accept()
    }

    private fun accept() {
        try {
            val socket = ServerSocket(port)
            println("socks5 server listen on port: $port")
            while (true) {
                try {
                    val client = socket.accept()
                    handler.handle(client, true)
                    System.out.printf("accept client { %s }\n", client)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
