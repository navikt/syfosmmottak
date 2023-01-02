package no.nav.syfo.service

import io.mockk.clearAllMocks
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.duplicationcheck.model.Duplikatsjekk
import no.nav.syfo.util.TestDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class DuplicationServiceTest {
    private val testDatabase = TestDB
    private val duplicationService = DuplicationService(testDatabase.database)

    @BeforeEach
    internal fun setup() {
        clearAllMocks()
    }

    @Test
    fun `Should return duplicationCheck if sha256HealthInformation is in database`() {
        val sykmeldingID = "as344f11-23132-2dddef1s232df"

        val sha256HealthInformation = "asdsad"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplikatsjekk = Duplikatsjekk(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(), epjSystem, epjVersion, orgNumber
        )

        val duplicationCheck = DuplicateCheck(
            sykmeldingID, sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(),
            epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplikatsjekk, duplicationCheck)
        val isDuplicat = duplicationService.getDuplikatsjekk(sha256HealthInformation, mottakId)

        assertEquals(duplikatsjekk.sha256HealthInformation, isDuplicat?.sha256HealthInformation)
        assertEquals(duplikatsjekk.mottakId, isDuplicat?.mottakId)
        assertEquals(duplikatsjekk.msgId, isDuplicat?.msgId)
        assertEquals(duplikatsjekk.mottattDate.toLocalDate(), isDuplicat?.mottattDate?.toLocalDate())
        assertEquals(duplikatsjekk.epjSystem, isDuplicat?.epjSystem)
        assertEquals(duplikatsjekk.epjVersion, isDuplicat?.epjVersion)
        assertEquals(duplikatsjekk.orgNumber, isDuplicat?.orgNumber)
    }

    @Test
    fun `Should return null if sha256HealthInformation is not database`() {
        val sykmeldingID = "asdsdfsff11-23132-2dddefsdf"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplikatsjekk = Duplikatsjekk(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(), epjSystem, epjVersion, orgNumber
        )

        val duplicationCheck = DuplicateCheck(
            sykmeldingID, sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(),
            epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplikatsjekk, duplicationCheck)
        val isDuplicat = duplicationService.getDuplikatsjekk("1231", "1334")

        assertEquals(null, isDuplicat)
    }

    @Test
    fun `Should return duplicationCheck if mottakId is database`() {
        val sykmeldingID = "asdsadff11-23132-2ddde323"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplikatsjekk = Duplikatsjekk(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(), epjSystem, epjVersion, orgNumber
        )
        val duplicationCheck = DuplicateCheck(
            sykmeldingID, sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(),
            epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplikatsjekk, duplicationCheck)
        val isDuplicat = duplicationService.getDuplikatsjekk("1231", mottakId)

        assertEquals(mottakId, isDuplicat?.mottakId)
    }

    @Test
    fun `Should return duplicationCheck if epjSystem,epjVersion and orgNumber is null`() {
        val sykmeldingID = "asds23ff11-23132-2dhsddsffsdf"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"

        val duplikatsjekk = Duplikatsjekk(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(),
            null, null, null
        )
        val duplicationCheck = DuplicateCheck(
            sykmeldingID, sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(),
            "epj", "1", null
        )

        duplicationService.persistDuplicationCheck(duplikatsjekk, duplicationCheck)
        val isDuplicat = duplicationService.getDuplikatsjekk("1231", mottakId)

        assertEquals(duplikatsjekk.sha256HealthInformation, isDuplicat?.sha256HealthInformation)
        assertEquals(duplikatsjekk.mottakId, isDuplicat?.mottakId)
        assertEquals(duplikatsjekk.msgId, isDuplicat?.msgId)
        assertEquals(duplikatsjekk.mottattDate.toLocalDate(), isDuplicat?.mottattDate?.toLocalDate())
        assertEquals(null, duplikatsjekk.epjSystem)
        assertEquals(null, duplikatsjekk.epjVersion)
        assertEquals(null, duplikatsjekk.orgNumber)
    }
    @Test
    fun `Should persiste duplication in database`() {
        val sykmeldingID = "asd13111-23132-2dddefssddf-12345f"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val msgId = "12-33"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"
        val mottatDato = LocalDateTime.now()

        val duplikatsjekk = Duplikatsjekk(
            sha256HealthInformation, mottakId, msgId, mottatDato, epjSystem, epjVersion, orgNumber
        )
        val duplicationCheck = DuplicateCheck(
            sykmeldingID, sha256HealthInformation, mottakId, msgId, mottatDato, epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplikatsjekk, duplicationCheck)

        val isDuplicat = duplicationService.getDuplicationCheck("1231", mottakId)!!

        val duplication = Duplicate(
            sykmeldingID, mottakId, msgId,
            isDuplicat.sykmeldingId, mottatDato, epjSystem, epjVersion
        )

        duplicationService.persistDuplication(duplication)

        assertEquals(mottakId, isDuplicat.mottakId)
    }
}
