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

object DisplayUtils {

    enum class Unit(val scale: Long, val displayName: String) {
        GB(1024 * 1024 * 1024L, "GB"),
        MB(1024 * 1024L, "MB"),
        KB(1024L, "KB")
    }

    data class Bytes(val amount: Double, val unit: Unit) : Comparable<Bytes> {
        override fun toString(): String {
            return "${String.format("%.2f", amount)} ${unit.displayName}"
        }
        override fun compareTo(other: Bytes): Int {
            return (amount * unit.scale).compareTo(other.amount * other.unit.scale)
        }
    }

    fun toBytes(amount: Double) : Bytes {
        return Unit.entries.firstOrNull { it.scale < amount }?.let {
            Bytes(amount / it.scale, it)
        } ?: Bytes(amount, Unit.KB)
    }

}