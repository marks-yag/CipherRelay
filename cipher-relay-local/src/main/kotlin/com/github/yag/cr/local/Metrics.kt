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