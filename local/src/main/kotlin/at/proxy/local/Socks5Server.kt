package at.proxy.local

import java.net.InetSocketAddress

class Socks5Server(config: Socks5ServerConfig) {

    val acceptor = Socks5Acceptor(config.port, config.remoteEndpoint)

    init {
        acceptor.run()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val server = Socks5Server(Socks5ServerConfig().also {
                it.remoteEndpoint = InetSocketAddress("localhost", 9528)
            })
            Thread.currentThread().join()
        }
    }
}