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

import com.github.yag.cr.local.ProxyConnection
import java.util.concurrent.atomic.AtomicReference
import javax.swing.table.AbstractTableModel
import kotlin.collections.map
import java.util.ResourceBundle

class ConnectionTableModel(private val connections: AtomicReference<List<ProxyConnection>>) : AbstractTableModel() {
    private val bundle = ResourceBundle.getBundle("messages")

    val activeColumns: Array<Pair<String, (ProxyConnection) -> Any?>> = arrayOf(
        bundle.getString("connection.process.id") to { it.process?.processID },
        bundle.getString("connection.process.name") to { it.process?.name },
        bundle.getString("connection.remote.address") to { SocketAddress(it.connection.clientAddress)},
        bundle.getString("connection.type") to { it.connection.typeName() },
        bundle.getString("connection.target.address") to { it.connection.targetAddress() },
        bundle.getString("connection.downstream.traffic") to { DisplayUtils.toBytes(it.connection.getDownloadTrafficInBytes().toDouble()) },
        bundle.getString("connection.upstream.traffic") to { DisplayUtils.toBytes(it.connection.getUploadTrafficInBytes().toDouble()) }
    )

    private var connectionSnapshot: List<ProxyConnection> = connections.get()

    override fun getRowCount(): Int {
        return connectionSnapshot.size
    }

    override fun getColumnCount(): Int {
        return activeColumns.size
    }

    override fun getColumnName(column: Int): String {
        return activeColumns[column].first
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val connection = connectionSnapshot[rowIndex]
        return activeColumns[columnIndex].second.invoke(connection) ?: "N/A"
    }

    override fun fireTableDataChanged() {
        connectionSnapshot = connections.get().map { it }
        super.fireTableDataChanged()
    }
}