package at.proxy.local

import io.netty.channel.ChannelId
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

sealed class Connection(val clientAddress: InetSocketAddress) {
    val uploadTrafficInBytes = AtomicLong()
    val downloadTrafficInBytes = AtomicLong()

    abstract fun typeName(): String

    abstract fun targetAddress(): String
}

class Socks5Connection(clientAddress: InetSocketAddress, val requestAddress: String, val requestPort: Int) : Connection(clientAddress) {
    override fun toString(): String {
        return clientAddress.toString()
    }

    override fun typeName(): String {
        return "socks5"
    }

    override fun targetAddress(): String {
        return "$requestAddress:$requestPort"
    }
}

class HttpConnection(clientAddress: InetSocketAddress, private val type: HttpProxyType, val targetUri: String, val protocolVersion: String) : Connection(clientAddress) {
    override fun toString(): String {
        return "$clientAddress->$targetUri"
    }

    override fun typeName(): String {
        return when (type) {
            HttpProxyType.WEB -> "http"
            HttpProxyType.TUNNEL -> "https"
        }
    }

    override fun targetAddress(): String {
        return targetUri
    }
}

class Stat {
    val uploadTrafficInBytes = AtomicLong()
    val downloadTrafficInBytes = AtomicLong()
}

class ConnectionManager {
    private val connections = ConcurrentSkipListMap<ChannelId, Connection>()
    private val stat = ConcurrentSkipListMap<String, Stat>()

    fun addHttpConnection(id: ChannelId, connection: Connection) {
        connections[id] = connection
    }

    fun getConnection(id: ChannelId) = connections[id] ?: throw NoSuchElementException()

    fun removeConnection(id: ChannelId) {
        connections.remove(id)?.let { 
            stat.getOrPut(it.targetAddress()) {
                Stat()
            }.apply { 
                uploadTrafficInBytes.addAndGet(it.uploadTrafficInBytes.get())
                downloadTrafficInBytes.addAndGet(it.downloadTrafficInBytes.get())
                println("hello: $stat")
            }
        }
    }

    fun getAllConnections() : Collection<Connection> = connections.values
    
    fun getStat() = stat.map { it .key to it.value }
}