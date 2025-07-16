package no.kartverket.nibas.download

import java.sql.Connection

/**
 * Combined bulk import script for both grensepunkt and grenselinjer.
 *
 * 1. Get one endringsnummer from Oracle
 * 2. Import both datasets
 * 3. Save endringsnummer bookmark for changelog sync
 *
 */

/**
 * Get latest endringsnummer from Oracle ENDRING table.
 */
fun getSnapshotEndringsnummer(connection: Connection): Long {
    val query = "SELECT MAX(ID) FROM ENDRING"

    connection.createStatement().use { stmt ->
        val rs = stmt.executeQuery(query)
        return if (rs.next()) {
            val endringsnummer = rs.getLong(1)
            println("Latest endringsnummer from Oracle: $endringsnummer")
            endringsnummer
        } else {
            throw RuntimeException("Could not get endringsnummer from Oracle M22 ENDRING table")
        }
    }
}

/**
 * Save endringsnummer bookmark to PostgreSQL for changelog sync.
 */
fun saveEndringsnummerBookmark(endringsnummer: Long) {
    ArbeidslisteDbConnector().use { connector ->
        connector.connection.prepareStatement(
            """INSERT INTO nibas_arbeidsliste_schema.matrikkel_endringsnummer(endringsnummer, oppdateringsdato) 
               VALUES (?, CURRENT_TIMESTAMP) 
               ON CONFLICT (endringsnummer) DO NOTHING"""
        ).use { ps ->
            ps.setLong(1, endringsnummer)
            val rowsInserted = ps.executeUpdate()
            if (rowsInserted > 0) {
                println("Saved endringsnummer bookmark: $endringsnummer")
            } else {
                println("Endringsnummer $endringsnummer already exists in database")
            }
        }
        connector.commit()
    }
}

fun runBulkImport() {
    println("Starting combined bulk import for grensepunkt + grenselinjer...")

    M22DbConnector().use { m22Connector ->
        val oracleConnection = m22Connector.connection

        try {
            val snapshotEndringsnummer = getSnapshotEndringsnummer(oracleConnection)

            println("\n=== IMPORTING GRENSELINJER ===")
            importRawGrenselinjer(oracleConnection)

            println("\n=== IMPORTING GRENSEPUNKT ===")
            importRawGrensepunkt(oracleConnection)

            println("\n=== SAVING ENDRINGSNUMMER BOOKMARK ===")
            saveEndringsnummerBookmark(snapshotEndringsnummer)

            println("\n=== BULK IMPORT COMPLETED ===")
            println("Both datasets imported using endringsnummer: $snapshotEndringsnummer")
            println("Changelog sync can now start from: ${snapshotEndringsnummer + 1}")

        } catch (e: Exception) {
            println("ERROR: Bulk import failed: ${e.message}")
            throw e
        }
    }
}

fun main() {
    runBulkImport()
}
