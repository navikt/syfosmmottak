package no.nav.syfo.apprec

import no.nav.syfo.fellesformatUnmarshaller
import no.nav.syfo.serializeAppRec
import no.nav.syfo.utils.getFileAsString
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import org.amshove.kluent.shouldContain
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object ApprecMarshallerSpek : Spek({
    describe("Serializing a apprec") {
        val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
        val apprec = createApprec(fellesformat, ApprecStatus.ok, listOf())
        val serializedApprec = serializeAppRec(apprec)

        it("Results in a XML without namespace prefixes") {
            serializedApprec shouldContain "<AppRec xmlns=\"http://www.kith.no/xmlstds/apprec/2004-11-21\">"
        }
    }
})