package at.proxy.desktop

import java.net.InetSocketAddress

class SocketAddress(val host: String, val port: Int) : Comparable<SocketAddress> {
    
    constructor(address: InetSocketAddress) : this(address.address.hostAddress, address.port)

    override fun toString(): String {
        return "$host:$port"
    }
    
    override fun equals(other: Any?): Boolean {
        return if (other is SocketAddress) {
            host == other.host && port == other.port
        } else {
            false
        }
    }
    
    override fun hashCode(): Int {
        return host.hashCode() * 31 + port
    }
    
    override fun compareTo(other: SocketAddress): Int {
        return when {
            host != other.host -> host.compareTo(other.host)
            else -> port.compareTo(other.port)
        }
    }
}