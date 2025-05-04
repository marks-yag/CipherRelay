package at.proxy.desktop

import at.proxy.local.ProxyConnection
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
        bundle.getString("connection.download.traffic") to { DisplayUtils.toBytes(it.connection.getDownloadTrafficInBytes().toDouble()) },
        bundle.getString("connection.upload.traffic") to { DisplayUtils.toBytes(it.connection.getUploadTrafficInBytes().toDouble()) }
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