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

import com.github.yag.cr.local.Stat
import java.util.ResourceBundle
import java.util.concurrent.atomic.AtomicReference
import javax.swing.table.AbstractTableModel
import kotlin.collections.toList

class StatTableModel(private val stats: AtomicReference<List<Pair<String, Stat>>>) : AbstractTableModel() {

    private val bundle = ResourceBundle.getBundle("messages")

    val statColumns: Array<Pair<String, (Pair<String, Stat>) -> Any>> = arrayOf(
        bundle.getString("stat.target.address") to { it.first },
        bundle.getString("stat.download.traffic") to { DisplayUtils.toBytes(it.second.downloadTrafficInBytes.toDouble()) },
        bundle.getString("stat.upload.traffic") to { DisplayUtils.toBytes(it.second.uploadTrafficInBytes.toDouble()) }
    )

    private var statsSnapshot: List<Pair<String, Stat>> = stats.get()

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