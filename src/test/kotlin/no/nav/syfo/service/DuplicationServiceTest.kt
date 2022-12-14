package no.nav.syfo.service

import io.mockk.clearAllMocks
import no.nav.syfo.duplicationcheck.model.DuplicationCheck
import no.nav.syfo.util.TestDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class DuplicationServiceTest {

    private val duplicationService = DuplicationService(TestDB.database)

    @BeforeEach
    internal fun `Setup`() {
        clearAllMocks()
    }

    @Test
    fun `Should return duplicationCheck if sha256HealthInformation is in database`() {
        val sha256HealthInformation = "asdsad"
        val mottakId = "1231-213"

        val duplicationCheck = DuplicationCheck(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now()
        )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck(sha256HealthInformation, mottakId)

        assertEquals(isDuplicat?.sha256HealthInformation, duplicationCheck.sha256HealthInformation)
        assertEquals(isDuplicat?.mottakId, duplicationCheck.mottakId)
        assertEquals(isDuplicat?.msgId, duplicationCheck.msgId)
        assertEquals(isDuplicat?.mottattDate?.toLocalDate(), duplicationCheck.mottattDate.toLocalDate())
    }

    @Test
    fun `Should return null if sha256HealthInformation is not database`() {
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"

        val duplicationCheck = DuplicationCheck(
            sha256HealthInformation, mottakId, "12-33", LocalDateTime.now()
        )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", "1334")

        assertEquals(null, isDuplicat)
    }
}
