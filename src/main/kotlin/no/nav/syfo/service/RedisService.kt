package no.nav.syfo.service

import java.util.concurrent.TimeUnit
import redis.clients.jedis.Jedis

fun updateRedis(jedis: Jedis, ediLoggId: String, sha256String: String) {
    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
}