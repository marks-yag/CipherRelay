package at.proxy.desktop

import at.proxy.local.Connection
import at.proxy.local.LocalConfig
import at.proxy.local.LocalServer
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

    private val config = AtomicReference<LocalConfig>()

    private val server = AtomicReference<LocalServer>()

    private val started = AtomicBoolean()

    private val timer = Executors.newSingleThreadScheduledExecutor()

    private val connections = AtomicReference(emptyList<Connection>())
    
    private val stats = AtomicReference(emptyList<Pair<String, Stat>>())

    private val mapper = ObjectMapper()

    val activeColumns = arrayOf("Remote Address", "Type", "Target Address", "Download Traffic", "Upload Traffic")

    val statColumns = arrayOf("Target Address", "Download Traffic", "Upload Traffic")

    private val proxyIcon = ImageIO.read(Desktop::class.java.getResource("/proxy.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH)
    private val configIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/config.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH))
    private val startIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/start.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH))
    private val stopIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/stop.png")).getScaledInstance(12, 12, Image.SCALE_SMOOTH))

    private val activeModel = object: AbstractTableModel() {

        private val mapping: Array<(Connection) -> Any> = arrayOf(
            Connection::clientAddress,
            Connection::typeName,
            { it.targetAddress() },
            { DisplayUtils.toBytes(it.getDownloadTrafficInBytes().toDouble()) },
            { DisplayUtils.toBytes(it.getUploadTrafficInBytes().toDouble()) }
        )

        override fun getRowCount(): Int {
            return connections.get().size
        }

        override fun getColumnCount(): Int {
            return mapping.size
        }

        override fun getColumnName(column: Int): String {
            return activeColumns[column]
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val connection = connections.get()[rowIndex]
            return mapping[columnIndex].invoke(connection)
        }
    }

    private val statModel = object: AbstractTableModel() {

        private val mapping: Array<(Pair<String, Stat>) -> Any> = arrayOf(
            { it.first },
            { DisplayUtils.toBytes(it.second.downloadTrafficInBytes.toDouble()) },
            { DisplayUtils.toBytes(it.second.uploadTrafficInBytes.toDouble()) }
        )

        override fun getRowCount(): Int {
            return stats.get().size
        }

        override fun getColumnCount(): Int {
            return mapping.size
        }

        override fun getColumnName(column: Int): String {
            return statColumns[column]
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val stat = stats.get()[rowIndex]
            return mapping[columnIndex].invoke(stat)
        }
    }

    init {
        timer.scheduleAtFixedRate({
            server.get()?.connectionManager?.let { 
                connections.set(it.getAllConnections().toList())
                stats.set(it.getStat().sortedByDescending { it.second.downloadTrafficInBytes.get() })
            }
            activeModel.fireTableDataChanged()
            statModel.fireTableDataChanged()
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
        FlatLightLaf.setup()
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


        val connectionsTable = JTable(activeModel)
        val dashboard = JScrollPane(connectionsTable)
        val statTable = JTable(statModel)
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
        toolBar.add(JButton("Start").also { button ->
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
        })
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

    private fun config(frame: JFrame, configFile: Path) : LocalConfig {
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

        configDialog.add(form.create {
            configFile.writeText(mapper.writeValueAsString(it))
            configDialog.isVisible = false
            config.set(it)
        })
        configDialog.setSize(300, 200)
        configDialog.setLocationRelativeTo(frame)
        configDialog.isVisible = true
        return LocalConfig()
    }

    private fun start(config: LocalConfig) {
        server.set(LocalServer(config))
    }

    private fun stop() {
        server.get().close()
    }
}