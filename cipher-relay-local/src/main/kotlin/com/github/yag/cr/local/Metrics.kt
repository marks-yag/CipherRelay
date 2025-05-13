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

package com.github.yag.cr.local

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry

class Metrics {

    private val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val downstreamTraffic: Counter = Counter.builder("downstream-traffic").baseUnit("bytes").register(registry)

    val downstreamTrafficEncrypted: Counter = Counter.builder("downstream-traffic-encrypted").baseUnit("bytes").register(registry)

    val upstreamTraffic: Counter = Counter.builder("upstream-traffic").baseUnit("bytes").register(registry)

    val upstreamTrafficEncrypted: Counter = Counter.builder("upstream-traffic-encrypted").baseUnit("bytes").register(registry)

    fun scrape(): String = registry.scrape()
}