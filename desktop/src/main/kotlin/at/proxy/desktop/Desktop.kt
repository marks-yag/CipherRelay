package at.proxy.desktop

import at.proxy.local.Connection
import at.proxy.local.HttpConnection
import at.proxy.local.LocalConfig
import at.proxy.local.LocalServer
import com.fasterxml.jackson.databind.ObjectMapper
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function
import javax.swing.*
import javax.swing.table.AbstractTableModel
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

class Desktop {

    private val config = AtomicReference<LocalConfig>()

    private val server = AtomicReference<LocalServer>()

    private val started = AtomicBoolean()

    private val timer = Executors.newSingleThreadScheduledExecutor()

    private val connections = AtomicReference<List<Connection>>(emptyList<Connection>())

    private val mapper = ObjectMapper()

    val columns = arrayOf("Remote Address", "Type", "Http Type", "Target URI", "Protocol Version", "Download Bytes", "Upload Bytes")

    val model = object: AbstractTableModel() {

        private val mapping: Array<(Connection) -> Any> = arrayOf(
            Connection::remoteAddress,
            Connection::typeName,
            { c -> if (c is HttpConnection) c.type else "" },
            { c -> if (c is HttpConnection) c.targetUri else "" },
            { c -> (c as? HttpConnection)?.protocolVersion?: ""},
            Connection::downloadTrafficInBytes,
            Connection::uploadTrafficInBytes
        )

        override fun getRowCount(): Int {
            return connections.get().size
        }

        override fun getColumnCount(): Int {
            return mapping.size
        }

        override fun getColumnName(column: Int): String? {
            return columns[column]
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val connection = connections.get()[rowIndex]
            return mapping[columnIndex].invoke(connection)
        }
    }

    init {
        timer.scheduleAtFixedRate(Runnable {
            server.get()?.connectionManager?.getAllConnections()?.let { connections.set(it.toList()) }
            model.fireTableDataChanged()
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
        JFrame.setDefaultLookAndFeelDecorated(false)
        val frame = JFrame("At Proxy")
        frame.setBounds(600, 600, 600, 600)
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


        val connectionsTable = JTable(model)
        val dashboard = JScrollPane(connectionsTable)

        frame.add(dashboard, BorderLayout.CENTER)
        frame.isVisible = true
    }

    private fun Desktop.createToolBar(
        frame: JFrame,
        configFile: Path
    ): JToolBar {
        val toolBar = JToolBar()
        toolBar.add(JButton("Configure...").also {
            it.addActionListener {
                config(frame, configFile)
            }
        })
        toolBar.add(JButton("Start").also { button ->
            button.addActionListener {
                if (started.compareAndSet(false, true)) {
                    start(config.get())
                    button.text = "Stop"
                } else {
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
                    label.text = server.get().metrics.upstreamTrafficEncrypted.count().toString()
                }
            }.start()
        })
        statusBar.add(JLabel("bytes/sec"))

        statusBar.add(JLabel("Downstream:"))
        statusBar.add(JLabel("N/A").also { label ->
            Timer(1000) {
                server.get()?.let {
                    label.text = server.get().metrics.downstreamTrafficEncrypted.count().toString()
                }
            }.start()
        })
        statusBar.add(JLabel("bytes/sec"))
        return statusBar
    }

    private fun config(frame: JFrame, configFile: Path) : LocalConfig {
        val configDialog = JDialog(frame, "Configure")
        val panel = JPanel()
        panel.add(JLabel("Local Port:"))
        val jTextFieldLocalPort = JTextField(config.get().port.toString(), 32)
        panel.add(jTextFieldLocalPort)
        panel.add(JLabel("Remote Address:"))
        val jTextFieldRemoteAddress = JTextField(config.get().remoteEndpoint, 32)
        panel.add(jTextFieldRemoteAddress)
        panel.add(JLabel("Shared Key"))
        val jPasswordFieldSharedKey = JPasswordField(config.get().key, 32)
        panel.add(jPasswordFieldSharedKey)
        panel.add(JButton("Save").also {
            it.addActionListener {
                val newConfig = LocalConfig()
                newConfig.port = jTextFieldLocalPort.text.toInt()
                newConfig.remoteEndpoint = jTextFieldRemoteAddress.text
                newConfig.key = String(jPasswordFieldSharedKey.password)
                configFile.writeText(mapper.writeValueAsString(newConfig))
                configDialog.isVisible = false
                config.set(newConfig)
            }
        })
        configDialog.add(panel)
        configDialog.setBounds(700, 700, 300, 300)
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