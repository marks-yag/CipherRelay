package at.proxy.desktop

import at.proxy.local.LocalConfig
import at.proxy.local.LocalServer
import at.proxy.local.ProxyConnection
import at.proxy.local.Stat
import com.fasterxml.jackson.databind.ObjectMapper
import com.formdev.flatlaf.FlatLightLaf
import java.awt.BorderLayout
import java.awt.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.table.AbstractTableModel
import kotlin.io.path.readBytes
import kotlin.io.path.writeText


class Desktop {

    init {
        FlatLightLaf.setup()
    }

    private val config = AtomicReference<LocalConfig>()

    private val server = AtomicReference<LocalServer>()

    private val started = AtomicBoolean()

    private val timer = Executors.newSingleThreadScheduledExecutor()
    
    private val configFile = Paths.get(System.getProperty("user.home"), ".atproxy", "config.json")

    private val connections = AtomicReference(emptyList<ProxyConnection>())
    
    private val stats = AtomicReference(emptyList<Pair<String, Stat>>())

    private val mapper = ObjectMapper()

    val activeColumns: Array<Pair<String, (ProxyConnection) -> Any?>> = arrayOf(
        "Process ID" to { it.process?.processID },
        "Process Name" to { it.process?.name },
        "Remote Address" to { it.connection.clientAddress},
        "Type" to { it.connection.typeName() },
        "Target Address" to { it.connection.targetAddress() },
        "Download Traffic" to { DisplayUtils.toBytes(it.connection.getDownloadTrafficInBytes().toDouble()) },
        "Upload Traffic" to { DisplayUtils.toBytes(it.connection.getUploadTrafficInBytes().toDouble()) }
    )

    val statColumns: Array<Pair<String, (Pair<String, Stat>) -> Any>> = arrayOf(
        "Target Address" to { it.first },
        "Download Traffic" to { DisplayUtils.toBytes(it.second.downloadTrafficInBytes.toDouble()) },
        "Upload Traffic" to { DisplayUtils.toBytes(it.second.uploadTrafficInBytes.toDouble()) }
    )

    private val proxyIcon = ImageIO.read(Desktop::class.java.getResource("/proxy.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH)
    private val configIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/config.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH))
    private val startIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/start.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH))
    private val stopIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/stop.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH))

    private val activeModel = object: AbstractTableModel() {
        
        private var connectionSnapshot: List<ProxyConnection> = connections.get().toList()

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

    private val statModel = object: AbstractTableModel() {
        
        private var statsSnapshot: List<Pair<String, Stat>> = stats.get().toList()

        override fun getRowCount(): Int {
            return statsSnapshot.size
        }

        override fun getColumnCount(): Int {
            return statColumns.size
        }

        override fun getColumnName(column: Int): String {
            return statColumns[column].first
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val stat = statsSnapshot[rowIndex]
            return statColumns[columnIndex].second.invoke(stat)
        }

        override fun fireTableDataChanged() {
            statsSnapshot = stats.get().toList()
            super.fireTableDataChanged()
        }
    }

    val connectionTable = JTable(activeModel).also {
        it.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }
    val statTable = JTable(statModel).also {
        it.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
    }

    init {
        timer.scheduleAtFixedRate({
            server.get()?.connectionManager?.let {
                connections.set(it.getAllConnections().toList())
                stats.set(it.getStat().sortedByDescending { it.second.downloadTrafficInBytes.get() })
            }
            val selectedLocalAddress = connectionTable.selectedRow.takeIf { it != -1 }?.let {
                connectionTable.model.getValueAt(it, 3)
            }

            activeModel.fireTableDataChanged()
            (0 until activeModel.rowCount).firstOrNull() { row ->
                activeModel.getValueAt(row, 3) == selectedLocalAddress
            }?.let {
                connectionTable.changeSelection(it, 3, false, false)
            }
            
            val selectedTargetAddress = statTable.selectedRow.takeIf { it!= -1 }?.let {
                statTable.model.getValueAt(it, 0)
            }
            statModel.fireTableDataChanged()
            (0 until statModel.rowCount).firstOrNull() { row ->
                statModel.getValueAt(row, 0) == selectedTargetAddress
            }?.let {
                statTable.changeSelection(it, 0, false, false)
            }
        }, 1, 1, TimeUnit.SECONDS)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val desktop = Desktop()
            SwingUtilities.invokeLater {
                desktop.show()
            }
        }
    }

    private fun show () {

        val frame = JFrame("Proxy")
        frame.setSize(1000, 600)
        frame.isLocationByPlatform = true
        frame.iconImage = proxyIcon
        frame.layout = BorderLayout()
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val configFile = Paths.get(System.getProperty("user.home"), ".at-proxy")
        if (Files.exists(configFile)) {
            mapper.readValue(configFile.readBytes(), LocalConfig::class.java).let { config.set(it) }
        } else {
            config.set(LocalConfig())
            config(frame, configFile)
        }

        val toolBar = createToolBar(frame, configFile)
        frame.add(toolBar, "North")

        val statusBar = createStatusBar()
        frame.add(statusBar, "South")
        
        val dashboard = JScrollPane(connectionTable)
        val stat = JScrollPane(statTable)
        val tab = JTabbedPane()
        tab.add("Active", dashboard)
        tab.add("Stat", stat)

        frame.add(tab, BorderLayout.CENTER)
        frame.isVisible = true
    }

    private fun Desktop.createToolBar(
        frame: JFrame,
        configFile: Path
    ): JToolBar {
        val toolBar = JToolBar()
        toolBar.add(JButton("Configure...").also {
            it.icon = configIcon
            it.addActionListener {
                config(frame, configFile)
            }
        })
        val button = JButton("Start").also { button ->
            button.icon = startIcon
            button.addActionListener {
                if (started.compareAndSet(false, true)) {
                    start(config.get())
                    button.icon = stopIcon
                    button.text = "Stop"
                } else {
                    button.icon = startIcon
                    button.text = "Start"
                    started.set(false)
                    stop()
                }
            }
        }
        toolBar.add(button)
        if (config.get().autoStart) {
            button.doClick()
        }
        return toolBar
    }

    private fun createStatusBar(): JPanel {
        val statusBar = JPanel()
        statusBar.add(JLabel("Upstream:"))
        statusBar.add(JLabel("N/A").also { label ->
            Timer(1000) {
                server.get()?.let {
                    label.text = DisplayUtils.toBytes(server.get().metrics.upstreamTrafficEncrypted.count()).toString()
                }
            }.start()
        })

        statusBar.add(JLabel("Downstream:"))
        statusBar.add(JLabel("N/A").also { label ->
            Timer(1000) {
                server.get()?.let {
                    label.text = DisplayUtils.toBytes(server.get().metrics.downstreamTrafficEncrypted.count()).toString()
                }
            }.start()
        })
        
        statusBar.add(JLabel().also { label -> 
            Timer(1000) {
                server.get()?.let { 
                    label.text = "Listening: ${it.getEndpoint()}"
                }
            }.start()
        })
        
        return statusBar
    }

    private fun config(frame: JFrame, configFile: Path) {
        val configDialog = JDialog(frame, "Configure")
        val form = Form {
            LocalConfig()
        }
        form.add("Local Port:", JTextField(config.get().port.toString(), 32)) { input, config ->
            config.port = input.text.toInt()
        }
        form.add("Remote Address:", JTextField(config.get().remoteEndpoint, 32)) { input, config ->
            config.remoteEndpoint = input.text
        }
        form.add("Shared Key:", JPasswordField(config.get().key, 32)) { input, config ->
            config.key = String(input.password)
        }
        form.add("Auto Start:", JCheckBox("", config.get().autoStart)) { input, config ->
            config.autoStart = input.isSelected
        }

        configDialog.add(form.create {
            configFile.writeText(mapper.writeValueAsString(it))
            configDialog.isVisible = false
            config.set(it)
        })
        configDialog.setSize(300, 200)
        configDialog.setLocationRelativeTo(frame)
        configDialog.isVisible = true
    }

    private fun start(config: LocalConfig) {
        server.set(LocalServer(config))
    }

    private fun stop() {
        server.get().close()
    }
}