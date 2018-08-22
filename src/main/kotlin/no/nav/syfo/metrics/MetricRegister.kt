package no.nav.syfo.metrics

import io.prometheus.client.Summary

const val METRICS_NS = "syfomottak"

val REQUEST_TIME: Summary = Summary.build()
        .namespace(METRICS_NS)
        .name("request_time_ms")
        .help("Request time in milliseconds.").register()