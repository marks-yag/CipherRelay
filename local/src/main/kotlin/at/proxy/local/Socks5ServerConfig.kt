package at.proxy.local

import config.Value
import ketty.core.common.ChannelConfig
import java.net.InetSocketAddress
import java.net.SocketAddress

class Socks5ServerConfig {

    @Value
    var port = 9527

    @Value
    var parentThreads = 4

    @Value
    var childThreads = 8

    @Value
    var channelConfig = ChannelConfig()

    @Value
    var credentials = HashMap<String, String>()

    @Value
    lateinit var remoteEndpoint: InetSocketAddress

}