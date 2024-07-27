package at.proxy.desktop

import at.proxy.local.LocalConfig
import at.proxy.local.LocalServer
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

class Desktop {

    private val config = AtomicReference<LocalConfig>()

    private val server = AtomicReference<LocalServer>()

    private val mapper = ObjectMapper()

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
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

        val configFile = Paths.get(System.getProperty("user.home"), ".at-proxy")
        if (Files.exists(configFile)) {
            mapper.readValue(configFile.readBytes(), LocalConfig::class.java).let { config.set(it) }
        } else {
            config.set(LocalConfig())
            config(frame, configFile)
        }
        val rootPanel = JPanel()
        rootPanel.add(JButton("Configure...").also {
            it.addActionListener {
                config(frame, configFile)
            }
        })
        rootPanel.add(JButton("Start").also {
            it.addActionListener {
                start(config.get())
            }
        })
        rootPanel.add(JButton("Stop").also {
            it.addActionListener {
                stop()
            }
        })

        frame.add(rootPanel)
        frame.isVisible = true
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