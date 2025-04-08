package no.nav.syfo.util

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

fun gzip(input: String): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(input.toByteArray(Charsets.UTF_8)) }
    return out.toByteArray()
}
