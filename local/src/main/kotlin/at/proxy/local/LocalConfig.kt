package at.proxy.local

import config.Value
import java.net.InetSocketAddress

class LocalConfig {

    @Value
    var port = 9527

    @Value
    val metricPort = 19527

    @Value
    var key = "at-proxy"

    @Value
    var remoteEndpoint: InetSocketAddress = InetSocketAddress("127.0.0.1", 9528)

}