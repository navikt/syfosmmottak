package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.io.StringWriter
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Marshaller.JAXB_ENCODING
import javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT
import javax.xml.bind.Unmarshaller
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

val fellesformatJaxBContext: JAXBContext =
    JAXBContext.newInstance(
        XMLEIFellesformat::class.java,
        XMLMsgHead::class.java,
        HelseOpplysningerArbeidsuforhet::class.java
    )
val fellesformatUnmarshaller: Unmarshaller =
    fellesformatJaxBContext.createUnmarshaller().apply {
        setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
        setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
    }

val fellesformatMarshaller: Marshaller =
    fellesformatJaxBContext.createMarshaller().apply {
        setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
        setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
        setProperty(JAXB_ENCODING, "UTF-8")
        setProperty(JAXB_FORMATTED_OUTPUT, true)
    }

val senderMarshaller: Marshaller =
    JAXBContext.newInstance(XMLSender::class.java).createMarshaller().apply {
        setProperty(JAXB_ENCODING, "ISO-8859-1")
    }

fun Marshaller.toString(input: Any): String =
    StringWriter().use {
        marshal(input, it)
        it.toString()
    }
