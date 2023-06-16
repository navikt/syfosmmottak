package no.nav.syfo.duplicationcheck.db

import java.sql.ResultSet
import java.sql.Timestamp
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck

fun DatabaseInterface.persistDuplicateCheck(duplicateCheck: DuplicateCheck) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
            insert into duplicatecheck(
                sykmelding_id,
                sha256_health_information,
                mottak_id,
                msg_id,
                mottatt_date,
                epj_system,
                epj_version,
                org_number,
                ruleset_version
                )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?);
            """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, duplicateCheck.sykmeldingId)
                preparedStatement.setString(2, duplicateCheck.sha256HealthInformation)
                preparedStatement.setString(3, duplicateCheck.mottakId)
                preparedStatement.setString(4, duplicateCheck.msgId)
                preparedStatement.setTimestamp(5, Timestamp.valueOf(duplicateCheck.mottattDate))
                preparedStatement.setString(6, duplicateCheck.epjSystem)
                preparedStatement.setString(7, duplicateCheck.epjVersion)
                preparedStatement.setString(8, duplicateCheck.orgNumber)
                preparedStatement.setString(9, duplicateCheck.rulesetVersion)
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun DatabaseInterface.extractDuplicateCheckBySha256HealthInformation(
    sha256HealthInformation: String
): DuplicateCheck? {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
                 select * 
                 from duplicatecheck 
                 where sha256_health_information=?;
                """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, sha256HealthInformation)
                return preparedStatement.executeQuery().toList { toDuplicateCheck() }.firstOrNull()
            }
    }
}

fun DatabaseInterface.extractDuplicateCheckByMottakId(mottakId: String): List<DuplicateCheck> {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
                 select * 
                 from duplicatecheck 
                 where mottak_id=?;
                """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, mottakId)
                return preparedStatement.executeQuery().toList { toDuplicateCheck() }
            }
    }
}

fun DatabaseInterface.persistDuplicateMessage(duplicate: Duplicate) {
    connection.use { connection ->
        connection
            .prepareStatement(
                """
            insert into duplicate(
                sykmelding_id,
                mottak_id,
                msg_id,
                duplicate_sykmelding_id,
                mottatt_date,
                epj_system,
                epj_version,
                org_number
                )
            values (?, ?, ?, ?, ?, ?, ?, ?);
            """,
            )
            .use { preparedStatement ->
                preparedStatement.setString(1, duplicate.sykmeldingId)
                preparedStatement.setString(2, duplicate.mottakId)
                preparedStatement.setString(3, duplicate.msgId)
                preparedStatement.setString(4, duplicate.duplicateSykmeldingId)
                preparedStatement.setTimestamp(5, Timestamp.valueOf(duplicate.mottattDate))
                preparedStatement.setString(6, duplicate.epjSystem)
                preparedStatement.setString(7, duplicate.epjVersion)
                preparedStatement.setString(8, duplicate.orgNumber)
                preparedStatement.executeUpdate()
            }
        connection.commit()
    }
}

fun ResultSet.toDuplicateCheck(): DuplicateCheck =
    DuplicateCheck(
        sykmeldingId = getString("sykmelding_id"),
        sha256HealthInformation = getString("sha256_health_information"),
        mottakId = getString("mottak_id"),
        msgId = getString("msg_id"),
        mottattDate = getTimestamp("mottatt_date").toLocalDateTime(),
        epjSystem = getString("epj_system"),
        epjVersion = getString("epj_version"),
        orgNumber = getString("org_number"),
        rulesetVersion = getString("ruleset_version"),
    )
