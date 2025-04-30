package at.proxy.local

import io.netty.channel.ChannelId
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

import oshi.SystemInfo
import oshi.software.os.OperatingSystem

sealed class Connection(val clientAddress: InetSocketAddress, val connectionManager: ConnectionManager) {
    private val uploadTrafficInBytes = AtomicLong()
    private val downloadTrafficInBytes = AtomicLong()
    
    val pid = ProcessResolver.getConnectionDetails(clientAddress.port)
    
    val processName = pid?.let { ProcessResolver.getProcessName(it) }
    
    fun getUploadTrafficInBytes() = uploadTrafficInBytes.get()
    
    fun getDownloadTrafficInBytes() = downloadTrafficInBytes.get()
    
    fun increaseUploadTrafficInBytes(amount: Long) {
        uploadTrafficInBytes.addAndGet(amount)
        connectionManager.increaseUploadTrafficInBytes(this, amount)
    }
    
    fun increaseDownloadTrafficInBytes(amount: Long) {
        downloadTrafficInBytes.addAndGet(amount)
        connectionManager.increaseDownloadTrafficInBytes(this, amount)
    }

    abstract fun typeName(): String

    abstract fun targetAddress(): String
}

class Socks5Connection(clientAddress: InetSocketAddress, private val requestAddress: String, private val requestPort: Int, connectionManager: ConnectionManager) : Connection(clientAddress, connectionManager) {
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

class HttpConnection(clientAddress: InetSocketAddress, private val type: HttpProxyType, private val targetUri: String, connectionManager: ConnectionManager) : Connection(clientAddress, connectionManager) {
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

    fun addConnection(id: ChannelId, connection: Connection) {
        connections[id] = connection
        stat.computeIfAbsent(connection.targetAddress()) {
            Stat()
        }
    }

    fun getConnection(id: ChannelId) = connections[id] ?: throw NoSuchElementException()

    fun removeConnection(id: ChannelId) {
        connections.remove(id)
    }

    fun increaseUploadTrafficInBytes(connection: Connection, amount: Long) {
        stat[connection.targetAddress()]?.apply {
            uploadTrafficInBytes.addAndGet(amount)
        }
    }

    fun increaseDownloadTrafficInBytes(connection: Connection, amount: Long) {
        stat[connection.targetAddress()]?.apply { 
            downloadTrafficInBytes.addAndGet(amount)
        }
    }

    fun getAllConnections() : Collection<Connection> = connections.values
    
    fun getStat() = stat.map { it .key to it.value }
}

private object ProcessResolver {
    private val systemInfo = SystemInfo()
    private val os: OperatingSystem = systemInfo.operatingSystem

    fun getConnectionDetails(port: Int) : Int? {
        return os.internetProtocolStats.connections
            .filter { it.localPort == port }
            .map {
                it.getowningProcessId()
            }.singleOrNull()
    }
    
    fun getProcessName(pid: Int): String? {
        return os.getProcess(pid)?.name
    }
}
