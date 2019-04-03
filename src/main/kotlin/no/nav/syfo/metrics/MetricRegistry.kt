package no.nav.syfo.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Summary

const val METRICS_NS = "syfosmmottak"

val REQUEST_TIME: Summary = Summary.build()
        .namespace(METRICS_NS)
        .name("request_time_ms")
        .help("Request time in milliseconds.").register()

val INCOMING_MESSAGE_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("incoming_message_count")
        .help("Counts the number of incoming messages")
        .register()

val APPREC_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("apprec_count")
        .help("Counts the number of apprec messages")
        .register()