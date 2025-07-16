package no.kartverket.nibas.download

fun verifyImport() {
    println("Starting import verification...")

    var success = true

    M22DbConnector().use { m22Connector ->
        val oracleConnection = m22Connector.connection

        ArbeidslisteDbConnector().use { pgConnector ->
            val pgConnection = pgConnector.connection

            val oracleGrensepunktCount = oracleConnection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("""
                    SELECT COUNT(*) FROM grensepunkt
                    WHERE id IN (
                        SELECT kurvestartpunktid FROM grenselinje WHERE administrativgrensekodeid > 0 AND kurvestartpunktid IS NOT NULL
                        UNION
                        SELECT kurveendpunktid FROM grenselinje WHERE administrativgrensekodeid > 0 AND kurveendpunktid IS NOT NULL
                    )
                """.trimIndent())
                rs.next()
                rs.getLong(1)
            }

            val pgGrensepunktCount = pgConnection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt")
                rs.next()
                rs.getLong(1)
            }

            println("Grensepunkt - Oracle: $oracleGrensepunktCount, PostgreSQL: $pgGrensepunktCount")

            if (oracleGrensepunktCount != pgGrensepunktCount) {
                println("FAILED: Grensepunkt count mismatch!")
                success = false
            } else {
                println("OK: Grensepunkt counts match")
            }

            val oracleGrenselinjerCount = oracleConnection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM grenselinje WHERE administrativgrensekodeid > 0")
                rs.next()
                rs.getLong(1)
            }

            val pgGrenselinjerCount = pgConnection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje")
                rs.next()
                rs.getLong(1)
            }

            println("Grenselinjer - Oracle: $oracleGrenselinjerCount, PostgreSQL: $pgGrenselinjerCount")

            if (oracleGrenselinjerCount != pgGrenselinjerCount) {
                println("FAILED: Grenselinjer count mismatch!")
                success = false
            } else {
                println("OK: Grenselinjer counts match")
            }
        }
    }

    if (success) {
        println("\nImport verification PASSED - all row counts match!")
    } else {
        println("\nImport verification FAILED - row count mismatches found!")
        System.exit(1)
    }
}

fun main() {
    verifyImport()
}
