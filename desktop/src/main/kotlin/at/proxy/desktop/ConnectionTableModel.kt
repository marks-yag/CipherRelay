package at.proxy.desktop

import at.proxy.local.ProxyConnection
import java.util.concurrent.atomic.AtomicReference
import javax.swing.table.AbstractTableModel
import kotlin.collections.map

class ConnectionTableModel(private val connections: AtomicReference<List<ProxyConnection>>) : AbstractTableModel() {

    val activeColumns: Array<Pair<String, (ProxyConnection) -> Any?>> = arrayOf(
        "Process ID" to { it.process?.processID },
        "Process Name" to { it.process?.name },
        "Remote Address" to { it.connection.clientAddress},
        "Type" to { it.connection.typeName() },
        "Target Address" to { it.connection.targetAddress() },
        "Download Traffic" to { DisplayUtils.toBytes(it.connection.getDownloadTrafficInBytes().toDouble()) },
        "Upload Traffic" to { DisplayUtils.toBytes(it.connection.getUploadTrafficInBytes().toDouble()) }
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