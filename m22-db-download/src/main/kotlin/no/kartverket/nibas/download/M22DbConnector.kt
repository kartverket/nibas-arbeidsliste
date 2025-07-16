package no.kartverket.nibas.download

import oracle.jdbc.OracleConnection
import java.sql.DriverManager

/**
 * Database connector for M22 Oracle database.
 * Reads connection details from environment variables:
 * - MATRIKKEL_DB_URL: JDBC URL for Oracle database
 * - MATRIKKEL_DB_USERNAME: Database username
 * - MATRIKKEL_DB_PASSWORD: Database password
 */
class M22DbConnector : AutoCloseable {

    private var _connection: OracleConnection? = null

    val connection: OracleConnection
        get() {
            if (_connection == null || _connection!!.isClosed) {
                val url = System.getenv("MATRIKKEL_DB_URL")
                    ?: error("MATRIKKEL_DB_URL environment variable not set")
                val username = System.getenv("MATRIKKEL_DB_USERNAME")
                    ?: error("MATRIKKEL_DB_USERNAME environment variable not set")
                val password = System.getenv("MATRIKKEL_DB_PASSWORD")
                    ?: error("MATRIKKEL_DB_PASSWORD environment variable not set")

                _connection = DriverManager.getConnection(url, username, password) as OracleConnection
                _connection!!.autoCommit = false

                // Set read-only transaction for consistent data reads
                _connection!!.createStatement().use { st ->
                    st.execute("SET TRANSACTION READ ONLY")
                }

                println("Successfully connected to M22 Oracle DB: $url")
            }
            return _connection!!
        }

    override fun close() {
        try {
            _connection?.close()
            println("M22 Oracle DB connection closed.")
        } catch (e: Exception) {
            System.err.println("Error closing M22 DB connection: ${e.message}")
            e.printStackTrace()
        }
    }
}
