/*
 * Copyright 2024-2025 marks.yag@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.yag.cr.local

import io.netty.channel.ChannelId
import oshi.SystemInfo
import oshi.software.os.InternetProtocolStats
import oshi.software.os.OSProcess
import oshi.software.os.OperatingSystem
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

sealed class Connection(val clientAddress: InetSocketAddress, val connectionManager: ConnectionManager) {
    private val uploadTrafficInBytes = AtomicLong()
    private val downloadTrafficInBytes = AtomicLong()
    
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

data class ProxyConnection(val connection: Connection, val ipConnection: InternetProtocolStats.IPConnection?, val process: OSProcess?)

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

    fun getAllConnections() : Collection<ProxyConnection> {
        val systemInfo = SystemInfo()
        val os: OperatingSystem = systemInfo.operatingSystem
        val portToConnection = os.internetProtocolStats.connections.groupBy { it.localPort }.mapValues { it.value.first() }
        
        return connections.values.map { 
            val ipConnection = portToConnection[it.clientAddress.port]
            val process = ipConnection?.let { os.getProcess(it.getowningProcessId()) }
            ProxyConnection(it, ipConnection, process)
        }
    }
    
    fun getStat() = stat.map { it .key to it.value }
}
