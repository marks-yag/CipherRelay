package at.proxy.local

import config.Value
import java.net.InetSocketAddress
import java.net.SocketAddress

class Socks5ServerConfig {

    @Value
    var port = 9527

    @Value
    var credentials = HashMap<String, String>()

    @Value
    lateinit var remoteEndpoint: InetSocketAddress
}