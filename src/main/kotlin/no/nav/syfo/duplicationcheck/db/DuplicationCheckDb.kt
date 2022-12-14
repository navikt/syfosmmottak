package no.nav.syfo.duplicationcheck.db

import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.db.toList
import no.nav.syfo.duplicationcheck.model.DuplicationCheck
import java.sql.ResultSet
import java.sql.Timestamp

fun DatabaseInterface.persistSha256(duplicationCheck: DuplicationCheck) {
    connection.use { connection ->
        connection.prepareStatement(
            """
            insert into duplikatsjekk(
                sha256_health_information,
                mottak_id,
                msg_id,
                mottatt_date
                )
            values (?, ?, ?, ?)
            """
        ).use { preparedStatement ->
            preparedStatement.setString(1, duplicationCheck.sha256HealthInformation)
            preparedStatement.setString(2, duplicationCheck.mottakId)
            preparedStatement.setString(3, duplicationCheck.msgId)
            preparedStatement.setTimestamp(4, Timestamp.valueOf(duplicationCheck.mottattDate))
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
        mottattDate = getTimestamp("mottatt_date").toLocalDateTime()
    )
