package at.proxy.local

import io.netty.channel.ChannelId
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentSkipListMap

sealed class Connection(val remoteAddress: InetSocketAddress) {
    abstract fun type(): String
}

class Socks5Connection(remoteAddress: InetSocketAddress) : Connection(remoteAddress) {
    override fun toString(): String {
        return remoteAddress.toString()
    }

    override fun type(): String {
        return "socks5"
    }
}

class HttpConnection(remoteAddress: InetSocketAddress, val type: HttpProxyType, val targetUri: String, val protocolVersion: String) : Connection(remoteAddress) {
    override fun toString(): String {
        return "$remoteAddress->$targetUri"
    }

    override fun type(): String {
        return "http/$type"
    }
}

class ConnectionManager {
    private val connections = ConcurrentSkipListMap<ChannelId, Connection>()

    fun addHttpConnection(id: ChannelId, connection: Connection) {
        connections[id] = connection
    }

    fun removeConnection(id: ChannelId) {
        connections.remove(id)
    }

    fun getAllConnections() : Collection<Connection> = connections.values
}