package no.nav.helse.slowtests.utils

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WithPostgresContainer {
    private val postgresContainer =
        PostgreSQLContainer("postgres:latest").apply {
            setCommand("postgres", "-c", "fsync=off", "-c", "log_statement=all", "-c", "wal_level=logical")

            withReuse(true)
            withLabel("app-navn", "test-database")
            withUsername("fritakagp")
            withPassword("fritakagp")

            println("Starter Postgres-container...")
            start()
            println(
                "Postgres-container er klar, med portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: $username og $password",
            )
        }

    val hikariConfig = createHikariConfig(postgresContainer)
    val dataSource = HikariDataSource(hikariConfig)

    @BeforeAll
    fun migrate() {
        migrate(hikariConfig)
    }

    @AfterAll
    fun stopContainer() {
        println("Stopper Postgres-container...")
        dataSource.close()
        postgresContainer.stop()
        println("Postgres-container er stoppet.")
    }
}

private fun createHikariConfig(container: PostgreSQLContainer): HikariConfig =
    HikariConfig().apply {
        jdbcUrl = container.jdbcUrl
        username = container.username
        password = container.password
        maximumPoolSize = 3
        driverClassName = "org.postgresql.Driver"
    }

private fun migrate(config: HikariConfig) {
    HikariDataSource(config)
        .also {
            Flyway
                .configure()
                .dataSource(it)
                .lockRetryCount(50)
                .load()
                .migrate()
        }.close()
}
