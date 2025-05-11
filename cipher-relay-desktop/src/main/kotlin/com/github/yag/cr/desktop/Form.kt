package at.proxy.desktop

import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.ResourceBundle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

fun interface Extractor<Input, Model> {
    
    fun extract(input: Input, model: Model)
    
}

class Form<R>(private val r: () -> R) {
    
    private val bundle = ResourceBundle.getBundle("messages")
    
    private val inputs = ArrayList<(R) -> Unit>()
    
    private val panel = JPanel(GridBagLayout())
    
    fun <T : JComponent> add(label: String, component: T, extractor: Extractor<T, R>) : Form<R> {
        panel.add(JLabel("$label:"), getConstraints().apply { 
            gridx = 0
            gridy = inputs.size
            weightx = 0.2
        })
        panel.add(component, getConstraints().apply { 
            gridx = 1
            gridy = inputs.size
            weightx = 0.8
        })
        inputs.add { r ->
            extractor.extract(component, r)
        }
        return this
    }
    
    fun create(callback: (R) -> Unit) : JPanel {
        panel.add(JButton(bundle.getString("config.submit")).apply {
            addActionListener {
                val obj = r()
                inputs.forEach { 
                    it.invoke(obj)
                }
                callback(obj)
            }
        }, getConstraints().apply { 
            gridy = inputs.size
            gridwidth = 2
        })
        return panel
    }

    private fun getConstraints() = GridBagConstraints().apply {
        fill = GridBagConstraints.HORIZONTAL
        insets = Insets(5, 5, 5, 5)
    }
}