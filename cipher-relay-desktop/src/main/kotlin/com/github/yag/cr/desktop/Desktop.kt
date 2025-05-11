package com.github.yag.cr.desktop

import com.github.yag.cr.local.LocalConfig
import com.github.yag.cr.local.LocalServer
import com.github.yag.cr.local.ProxyConnection
import com.github.yag.cr.local.Stat
import com.fasterxml.jackson.databind.ObjectMapper
import com.formdev.flatlaf.FlatLightLaf
import java.awt.BorderLayout
import java.awt.Image
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ResourceBundle
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.table.TableRowSorter
import kotlin.io.path.readBytes
import kotlin.io.path.writeText


private const val ICON_SIZE = 12

class Desktop {

    private val bundle = ResourceBundle.getBundle("messages")

    init {
        FlatLightLaf.setup()
    }

    private val config = AtomicReference<LocalConfig>()

    private val server = AtomicReference<LocalServer>()

    private val started = AtomicBoolean()

    private val executor = Executors.newSingleThreadScheduledExecutor()
    
    private val connections = AtomicReference(emptyList<ProxyConnection>())
    
    private val stats = AtomicReference(emptyList<Pair<String, Stat>>())

    private val mapper = ObjectMapper()

    private val proxyIcon = ImageIO.read(Desktop::class.java.getResource("/favicon.png")).getScaledInstance(
        ICON_SIZE,
        ICON_SIZE, Image.SCALE_SMOOTH)
    private val configIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/config.png")).getScaledInstance(
        ICON_SIZE,
        ICON_SIZE, Image.SCALE_SMOOTH))
    private val startIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/start.png")).getScaledInstance(
        ICON_SIZE,
        ICON_SIZE, Image.SCALE_SMOOTH))
    private val stopIcon = ImageIcon(ImageIO.read(Desktop::class.java.getResource("/stop.png")).getScaledInstance(
        ICON_SIZE,
        ICON_SIZE, Image.SCALE_SMOOTH))

    private val connectionTableModel = ConnectionTableModel(connections)

    private val statTableModel = StatTableModel(stats)

    private val connectionTable = JTable(connectionTableModel).also {
        it.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        val sorter = TableRowSorter(it.model)
        sorter.setComparator(5, Comparator.naturalOrder<DisplayUtils.Bytes>())
        sorter.setComparator(6, Comparator.naturalOrder<DisplayUtils.Bytes>())
        it.rowSorter = sorter
    }
    
    private val statTable = JTable(statTableModel).also {
        it.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        val sorter = TableRowSorter(it.model)
        sorter.setComparator(1, Comparator.naturalOrder<DisplayUtils.Bytes>())
        sorter.setComparator(2, Comparator.naturalOrder<DisplayUtils.Bytes>())
        it.rowSorter = sorter
    }

    private val upstreamTraffic = JLabel("N/A")

    private val downstreamTraffic = JLabel("N/A")

    private val endpoint = JLabel("N/A")

    init {
        executor.scheduleAtFixedRate({
            updateDashboardUI()
        }, 1, 1, TimeUnit.SECONDS)
    }

    private fun updateDashboardUI() {
        server.get()?.connectionManager?.let {
            connections.set(it.getAllConnections().toList())
            stats.set(it.getStat().sortedByDescending { it.second.downloadTrafficInBytes.get() })
        }
        val selectedLocalAddress = connectionTable.selectedRow.takeIf { it != -1 }?.let {
            connectionTable.model.getValueAt(it, 2)
        }

        connectionTableModel.fireTableDataChanged()
        (0 until connectionTableModel.rowCount).firstOrNull() { row ->
            connectionTableModel.getValueAt(row, 2) == selectedLocalAddress
        }?.let {
            connectionTable.changeSelection(it, 2, false, false)
        }

        val selectedTargetAddress = statTable.selectedRow.takeIf { it != -1 }?.let {
            statTable.model.getValueAt(it, 0)
        }
        statTableModel.fireTableDataChanged()
        (0 until statTableModel.rowCount).firstOrNull() { row ->
            statTableModel.getValueAt(row, 0) == selectedTargetAddress
        }?.let {
            statTable.changeSelection(it, 0, false, false)
        }

        SwingUtilities.invokeLater {
            server.get()?.let {
                upstreamTraffic.text = DisplayUtils.toBytes(it.metrics.upstreamTrafficEncrypted.count()).toString()
                downstreamTraffic.text = DisplayUtils.toBytes(it.metrics.downstreamTrafficEncrypted.count()).toString()

            }
            endpoint.text = server.get()?.getEndpoint()?.toString() ?: "N/A"
        }
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
        val frame = JFrame(bundle.getString("proxy"))
        frame.setSize(1000, 600)
        frame.isLocationByPlatform = true
        frame.iconImage = proxyIcon
        frame.layout = BorderLayout()
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        

        val configFile = Paths.get(System.getProperty("user.home"), ".at-proxy", "config.json")
        if (Files.exists(configFile)) {
            mapper.readValue(configFile.readBytes(), LocalConfig::class.java).let { config.set(it) }
        } else {
            config.set(LocalConfig())
            config(frame, configFile)
        }

        val toolBar = createToolBar(frame, configFile)
        frame.add(toolBar, BorderLayout.NORTH)

        val statusBar = createStatusBar()
        frame.add(statusBar, BorderLayout.SOUTH)
        
        val dashboard = JScrollPane(connectionTable)
        val stat = JScrollPane(statTable)
        val tab = JTabbedPane()
        tab.add(bundle.getString("connection.tab.name"), dashboard)
        tab.add(bundle.getString("stat.tab.name"), stat)

        frame.add(tab, BorderLayout.CENTER)
        frame.isVisible = true
    }

    private fun createToolBar(
        frame: JFrame,
        configFile: Path
    ): JToolBar {
        val toolBar = JToolBar()
        toolBar.add(JButton(bundle.getString("configure.button.text")).also {
            it.icon = configIcon
            it.addActionListener {
                config(frame, configFile)
            }
        })
        val startButton = JButton(bundle.getString("start.button.text")).also { button ->
            button.icon = startIcon
            button.addActionListener {
                button.isEnabled = false
                val future = if (started.compareAndSet(false, true)) {
                    button.icon = stopIcon
                    button.text = bundle.getString("stop.button.text")
                    start(config.get())
                } else {
                    button.icon = startIcon
                    button.text = bundle.getString("start.button.text")
                    started.set(false)
                    stop()
                }
                future.thenApply { _ ->
                    button.isEnabled = true
                }.exceptionally {
                    it.printStackTrace()
                    button.isEnabled = true
                }
            }
        }
        toolBar.add(startButton)
        if (config.get().autoStart) {
            startButton.doClick()
        }
        return toolBar
    }

    private fun createStatusBar(): JPanel {
        val statusBar = JPanel()
        statusBar.add(JLabel(bundle.getString("status.upstream") + ":"))
        statusBar.add(upstreamTraffic)

        statusBar.add(JLabel(bundle.getString("status.downstream") + ":"))
        statusBar.add(downstreamTraffic)
        
        statusBar.add(JLabel(bundle.getString("status.listening") + ":"))
        statusBar.add(endpoint)
        
        return statusBar
    }

    private fun config(frame: JFrame, configFile: Path) {
        val configDialog = JDialog(frame, bundle.getString("configure.dialog.title"))
        val form = Form {
            LocalConfig()
        }
        form.add(bundle.getString("config.local.port"), JTextField(config.get().port.toString(), 32)) { input, config ->
            config.port = input.text.toInt()
        }
        form.add(bundle.getString("config.remote.address"), JTextField(config.get().remoteEndpoint, 32)) { input, config ->
            config.remoteEndpoint = input.text
        }
        form.add(bundle.getString("config.shared.key"), JPasswordField(config.get().key, 32)) { input, config ->
            config.key = String(input.password)
        }
        form.add(bundle.getString("config.auto.start"), JCheckBox("", config.get().autoStart)) { input, config ->
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

    private fun start(config: LocalConfig) =
        CompletableFuture.runAsync({
            server.set(LocalServer(config))
        }, executor)

    private fun stop() =
        CompletableFuture.runAsync( {
            server.get()?.close()
        }, executor)
    
}