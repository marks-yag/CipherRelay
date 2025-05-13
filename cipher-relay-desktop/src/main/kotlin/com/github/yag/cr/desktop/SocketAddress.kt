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

package com.github.yag.cr.desktop

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