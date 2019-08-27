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

val INVALID_MESSAGE_NO_NOTICE: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("invalid_message_no_notice_count")
        .help("Counts the number of messages, that has not enough information to be sendt to the rule engine ")
        .register()

val AVVIST_ULIK_SENDER_OG_BEHANDLER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("avvist_ulik_sender_og_behandler_count")
        .help("Teller antall meldinger, der det er avvik mellom sender og behandler")
        .register()