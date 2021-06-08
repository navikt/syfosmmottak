package no.nav.syfo.service

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.objectMapper
import redis.clients.jedis.Jedis
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

fun updateRedis(jedis: Jedis, ediLoggId: String, sha256String: String) {
    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
}

fun sha256hashstring(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet))
        .fold("") { str, it -> str + "%02x".format(it) }
