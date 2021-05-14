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

val ULIK_SENDER_OG_BEHANDLER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("avvist_ulik_sender_og_behandler_count")
        .help("Teller antall meldinger, der det er avvik mellom sender og behandler")
        .register()

val TEST_FNR_IN_PROD: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("test_fnr_in_prod")
        .help("Counts the number of messages that contains a test fnr i prod")
        .register()

val NEW_DIAGNOSE_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("ny_diagnose_count")
    .help("Teller nye diagnosekoder")
    .register()

val SYKMELDING_VEDLEGG_COUNTER: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("sykmelding_med_vedlegg")
        .help("Antall sykmeldinger som inneholder vedlegg")
        .register()

val MANGLER_TSSIDENT: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("mangler_tssident")
        .help("Antall sykmeldinger der vi ikke fant TSS-ident")
        .register()

val IKKE_OPPDATERT_PARTNERREG: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name("ikke_oppdatert_partnerreg")
        .help("Antall sykmeldinger der vi ikke oppdaterte partnerregister")
        .register()
