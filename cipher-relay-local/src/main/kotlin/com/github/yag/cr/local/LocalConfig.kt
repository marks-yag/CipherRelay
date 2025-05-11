package at.proxy.local

import config.Value

class LocalConfig {

    @Value
    var port = 9527

    @Value
    var key = "at-proxy"

    @Value
    var remoteEndpoint = "127.0.0.1:9528"
    
    @Value
    var autoStart = false

}