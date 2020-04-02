package no.nav.syfo.sykmelding.kafka.model

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.syfoservice.kafka.model.KafkaMessageMetadata

data class SykmeldingSyfoserviceKafkaMessage(
    val metadata: KafkaMessageMetadata,
    val helseopplysninger: HelseOpplysningerArbeidsuforhet
)
