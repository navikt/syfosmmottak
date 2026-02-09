package no.nav.syfo.util

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.db.Database
import no.nav.syfo.db.DatabaseInterface
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class TestDB private constructor() {

    companion object {
        var database: DatabaseInterface
        var postgres =
            PostgreSQLContainer(DockerImageName.parse("postgres:14"))
                .withPassword("password")
                .withUsername("postgres")
                .withDatabaseName("postgres")

        init {
            postgres.start()
            val mockEnv = mockk<EnvironmentVariables>(relaxed = true)
            every { mockEnv.dbPort } returns postgres.firstMappedPort.toString()
            every { mockEnv.databaseUsername } returns "postgres"
            every { mockEnv.databasePassword } returns "password"
            every { mockEnv.dbName } returns "postgres"
            database = Database(mockEnv)
        }
    }
}
