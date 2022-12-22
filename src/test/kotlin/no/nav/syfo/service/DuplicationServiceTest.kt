package no.nav.syfo.service

import io.mockk.clearAllMocks
import no.nav.syfo.duplicationcheck.model.DuplicationCheck
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
        val sha256HealthInformation = "asdsad"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplicationCheck = DuplicationCheck(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(), epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck(sha256HealthInformation, mottakId)

        assertEquals(duplicationCheck.sha256HealthInformation, isDuplicat?.sha256HealthInformation)
        assertEquals(duplicationCheck.mottakId, isDuplicat?.mottakId)
        assertEquals(duplicationCheck.msgId, isDuplicat?.msgId)
        assertEquals(duplicationCheck.mottattDate.toLocalDate(), isDuplicat?.mottattDate?.toLocalDate())
        assertEquals(duplicationCheck.epjSystem, isDuplicat?.epjSystem)
        assertEquals(duplicationCheck.epjVersion, isDuplicat?.epjVersion)
        assertEquals(duplicationCheck.orgNumber, isDuplicat?.orgNumber)
    }

    @Test
    fun `Should return null if sha256HealthInformation is not database`() {
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplicationCheck = DuplicationCheck(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(), epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", "1334")

        assertEquals(null, isDuplicat)
    }

    @Test
    fun `Should return duplicationCheck if mottakId is database`() {
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplicationCheck = DuplicationCheck(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(), epjSystem, epjVersion, orgNumber
        )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", mottakId)

        assertEquals(mottakId, isDuplicat?.mottakId)
    }

    @Test
    fun `Should return duplicationCheck if epjSystem,epjVersion and orgNumber is null`() {
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"

        val duplicationCheck = DuplicationCheck(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now(),
            null, null, null
        )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", mottakId)

        assertEquals(duplicationCheck.sha256HealthInformation, isDuplicat?.sha256HealthInformation)
        assertEquals(duplicationCheck.mottakId, isDuplicat?.mottakId)
        assertEquals(duplicationCheck.msgId, isDuplicat?.msgId)
        assertEquals(duplicationCheck.mottattDate.toLocalDate(), isDuplicat?.mottattDate?.toLocalDate())
        assertEquals(null, duplicationCheck.epjSystem)
        assertEquals(null, duplicationCheck.epjVersion)
        assertEquals(null, duplicationCheck.orgNumber)
    }
}
