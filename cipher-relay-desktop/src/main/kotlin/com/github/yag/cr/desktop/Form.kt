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