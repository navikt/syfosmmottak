package no.nav.syfo.duplicationcheck.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.duplicationcheck.model.DuplicationCheck
import java.sql.ResultSet
import java.sql.Timestamp
import no.nav.syfo.duplicationcheck.model.Duplicate

fun DatabaseInterface.persistSha256(duplicationCheck: DuplicationCheck) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplikatsjekk(
                sha256_health_information,
                mottak_id,
                msg_id,
                mottatt_date,
                epj_system,
                epj_version,
                org_number 
                )
            values (?, ?, ?, ?, ?, ?, ?) on conflict (sha256_health_information) do nothing;
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplicationCheck.sha256HealthInformation)
            preparedStatement.setString(2, duplicationCheck.mottakId)
            preparedStatement.setString(3, duplicationCheck.msgId)
            preparedStatement.setTimestamp(4, Timestamp.valueOf(duplicationCheck.mottattDate))
            preparedStatement.setString(5, duplicationCheck.epjSystem)
            preparedStatement.setString(6, duplicationCheck.epjVersion)
            preparedStatement.setString(7, duplicationCheck.orgNumber)
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.persistDuplicateMessage(duplicate: Duplicate) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplicate(
                id,
                mottak_id,
                msg_id,
                duplicate_mottak_id,
                duplicate_msg_id,
                mottatt_date
                )
            values (?, ?, ?, ?, ?, ?);
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplicate.id)
            preparedStatement.setString(2, duplicate.mottakId)
            preparedStatement.setString(3, duplicate.msgId)
            preparedStatement.setString(4, duplicate.duplicateMottakId)
            preparedStatement.setString(5, duplicate.duplicateMsgId)
            preparedStatement.setTimestamp(6, Timestamp.valueOf(duplicate.mottattDate))
            preparedStatement.executeUpdate()
        }
        connection.commit()
    }
}

fun DatabaseInterface.extractDuplicationCheckBySha256HealthInformation(sha256HealthInformation: String): DuplicationCheck? {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplikatsjekk 
                 where sha256_health_information=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, sha256HealthInformation)
            return preparedStatement.executeQuery().toList { toDuplicationCheck() }.firstOrNull()
        }
    }
}

fun DatabaseInterface.extractDuplicationCheckByMottakId(mottakId: String): List<DuplicationCheck> {
    connection.use { connection ->
        connection.prepareStatement(
            """
                 select * 
                 from duplikatsjekk 
                 where mottak_id=?;
                """
        ).use { preparedStatement ->
            preparedStatement.setString(1, mottakId)
            return preparedStatement.executeQuery().toList { toDuplicationCheck() }
        }
    }
}

fun ResultSet.toDuplicationCheck(): DuplicationCheck =
    DuplicationCheck(
        sha256HealthInformation = getString("sha256_health_information"),
        mottakId = getString("mottak_id"),
        msgId = getString("msg_id"),
        mottattDate = getTimestamp("mottatt_date").toLocalDateTime(),
        epjSystem = getString("epj_system"),
        epjVersion = getString("epj_version"),
        orgNumber = getString("org_number")
    )
