package no.kartverket.nibas.download

import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

/**
 * Database connector for the arbeidsliste-api database.
 * Reads connection details from environment variables:
 * - ARBEIDSLISTE_DB_URL: JDBC URL for the database (e.g., jdbc:postgresql://localhost:5432/arbeidsliste)
 * - ARBEIDSLISTE_DB_USERNAME: Database username
 * - ARBEIDSLISTE_DB_PASSWORD: Database password
 */
class ArbeidslisteDbConnector : AutoCloseable {

    private var _connection: Connection? = null

    val connection: Connection
        get() {
            if (_connection == null || _connection!!.isClosed) {
                try {
                    val dbUrl = System.getenv("ARBEIDSLISTE_DB_URL")
                        ?: throw IllegalStateException("ARBEIDSLISTE_DB_URL environment variable is not set")
                    val dbUsername = System.getenv("ARBEIDSLISTE_DB_USERNAME")
                        ?: throw IllegalStateException("ARBEIDSLISTE_DB_USERNAME environment variable is not set")
                    val dbPassword = System.getenv("ARBEIDSLISTE_DB_PASSWORD")
                        ?: throw IllegalStateException("ARBEIDSLISTE_DB_PASSWORD environment variable is not set")

                    // Create connection properties
                    val props = Properties()
                    props.setProperty("user", dbUsername)
                    props.setProperty("password", dbPassword)

                    // Connect to the database
                    _connection = DriverManager.getConnection(dbUrl, props)
                    _connection?.autoCommit = false // We'll manage transactions manually
                    println("Successfully connected to Arbeidsliste DB: $dbUrl")
                } catch (e: SQLException) {
                    System.err.println("Failed to connect to Arbeidsliste DB: ${e.message}")
                    e.printStackTrace()
                    throw e // Re-throw to indicate connection failure
                }
            }
            return _connection!!
        }

    /**
     * Tests if a connection to the database can be established.
     */
    fun testConnection(): Boolean {
        return try {
            // The getter for 'connection' will attempt to connect if not already connected.
            !connection.isClosed
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Commits the current transaction.
     */
    fun commit() {
        if (_connection != null && !_connection!!.isClosed) {
            try {
                _connection!!.commit()
            } catch (e: SQLException) {
                System.err.println("Failed to commit transaction: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }

    /**
     * Rolls back the current transaction.
     */
    fun rollback() {
        if (_connection != null && !_connection!!.isClosed) {
            try {
                _connection!!.rollback()
            } catch (e: SQLException) {
                System.err.println("Failed to roll back transaction: ${e.message}")
                e.printStackTrace()
                throw e
            }
        }
    }

    override fun close() {
        try {
            _connection?.close()
            println("Arbeidsliste DB connection closed.")
        } catch (e: SQLException) {
            System.err.println("Error closing Arbeidsliste DB connection: ${e.message}")
            e.printStackTrace()
        }
    }
}

fun testDatabaseConnection() {
    try {
        ArbeidslisteDbConnector().use { connector ->
            if (connector.testConnection()) {
                println("Connection to arbeidsliste-api database successful!")
            } else {
                println("Connection to arbeidsliste-api database failed.")
            }
        }
    } catch (e: Exception) {
        println("An error occurred during connection test: ${e.message}")
        e.printStackTrace()
    }
}

fun testQueryAvvikTable() {
    ArbeidslisteDbConnector().use { connector ->
        try {
            println("Testing query on avvik table...")

            val sql = """
                SELECT id, status, registrert_dato
                FROM nibas_arbeidsliste_schema.avvik
                LIMIT 5
            """.trimIndent()

            val statement = connector.connection.createStatement()
            val resultSet = statement.executeQuery(sql)

            var rowCount = 0
            while (resultSet.next()) {
                rowCount++
                val id = resultSet.getLong("id")
                val status = resultSet.getString("status")
                val registrertDato = resultSet.getTimestamp("registrert_dato")

                println("Row $rowCount: ID=$id, Status=$status, Registrert=$registrertDato")
            }

            if (rowCount == 0) {
                println("Query executed successfully, but no rows were returned.")
            } else {
                println("Query executed successfully, returned $rowCount rows.")
            }

            connector.rollback()

        } catch (e: Exception) {
            System.err.println("❌ Error executing query: ${e.message}")
            e.printStackTrace()
            connector.rollback()
        }
    }
}

fun main() {
    testDatabaseConnection()
    testQueryAvvikTable()
}
