package no.kartverket.nibas.arblist.matrikkel.download

import no.kartverket.nibas.arblist.matrikkel.download.convert.ConvertedGrenselinjeFile
import java.io.File
import java.sql.Types
import java.sql.Date
import java.sql.Timestamp

fun importConvertedGrenselinjer(convertedDir: File, endringsnummer: Long? = null) {
    println("Listing contents of directory: ${convertedDir.absolutePath}")
    convertedDir.listFiles()?.forEach { f ->
        println("  - ${f.name} (file=${f.isFile}, dir=${f.isDirectory})")
    }
    val grenselinjeFiles = convertedDir.listFiles { f -> f.isFile && f.name.startsWith("matrikkel_grenselinje") && f.name.endsWith(".bin") }
        ?.sortedBy { it.name } ?: emptyList()
    if (grenselinjeFiles.isEmpty()) {
        System.err.println("ERROR: No grenselinje files found in directory: ${convertedDir.absolutePath}")
        System.err.println("Expected files like 'matrikkel_grenselinje.000.bin', etc.")
        return
    }
    ArbeidslisteDbConnector().use { connector ->
        val conn = connector.connection
        // First, create a temporary table to track duplicates
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TEMP TABLE temp_duplicate_ids (id BIGINT) ON COMMIT DROP;
                CREATE INDEX IF NOT EXISTS idx_temp_duplicate_ids ON temp_duplicate_ids(id);
            """.trimIndent())
        }

        conn.prepareStatement(
            """
            WITH inserted AS (
                INSERT INTO nibas_arbeidsliste_schema.matrikkel_grenselinje(
                    id, hjelpelinjetype_id, omtvistet, folgerterrengdetalj_id, administrativgrensekode_id, 
                    malemetode_id, noyaktighet, datafangstdato, lagretnoyaktighetsklasse, geom, oppdateringsdato,
                    kommunenr1, kommunenr2, informasjoncache, versjon, versjon_id, oppdatert_av
                ) VALUES (?,?,?,?,?,?,?,?,?,ST_GeomFromText(?, 25833),?,?,?,?,?,?,?)
                ON CONFLICT (id) DO NOTHING
                RETURNING id
            )
            SELECT id FROM inserted
            """.trimIndent()
        ).use { ps ->
            var imported = 0
            for (file in grenselinjeFiles) {
                val grenselinjer = ConvertedGrenselinjeFile(file.parentFile, "matrikkel_grenselinje")
                grenselinjer.forEach { entry ->
                    val grense = entry.grense()
                    if (grense.administrativGrenseKode().toInt() != 0) {
                        ps.setLong(1, entry.id())
                        ps.setObject(2, grense.hjelpelinjeKode().toInt(), Types.SMALLINT)
                        ps.setBoolean(3, grense.omtvistet())
                        ps.setObject(4, grense.terrengdetaljKode().toInt(), Types.SMALLINT)
                        ps.setObject(5, grense.administrativGrenseKode().toInt(), Types.SMALLINT)
                        ps.setObject(6, grense.maalemetodeKode().toInt(), Types.SMALLINT)
                        ps.setInt(7, grense.maalingsnoyaktighet())
                        val df = grense.datafangstdato()
                        if (df != null) {
                            ps.setDate(8, Date.valueOf("%04d-%02d-%02d".format(df.year(), df.month().toInt(), df.day().toInt())))
                        } else {
                            ps.setNull(8, Types.DATE)
                        }
                        ps.setObject(9, grense.noyaktighetsklasse().toInt(), Types.SMALLINT)
                        // Convert grenselinje coordinates to WKT (LINESTRING)
                        val coords = grense.coordinatesVector()
                        val points = (0 until coords.length()).map { idx ->
                            val c = coords.get(idx)
                            "${c.x().toDouble()} ${c.y().toDouble()}"
                        }
                        val wkt = "LINESTRING(" + points.joinToString(", ") + ")"
                        ps.setString(10, wkt)
                        val ts = grense.oppdateringsdato()
                        if (ts != null) {
                            ps.setTimestamp(11, Timestamp(ts.seconds() * 1000 + ts.nanos() / 1000000))
                        } else {
                            ps.setNull(11, Types.TIMESTAMP)
                        }
                        // Always ensure kommunenr1 is set, kommunenr2 can be null
                        val knrStr = grense.kommunenrstrengcache()
                        if (knrStr != null) {
                            val kommuner = knrStr.split(',').map { it.trim().padStart(4, '0') }
                            // First municipality number is required
                            ps.setString(12, kommuner.firstOrNull() ?: "0000")
                            // Second municipality number is optional
                            ps.setString(13, kommuner.getOrNull(1)?.takeIf { it.isNotBlank() })
                        } else {
                            // If no municipality string is available, use a default value for kommunenr1
                            ps.setString(12, "0000")
                            ps.setNull(13, Types.VARCHAR)
                        }

                        val infoStr = grense.informasjon()
                        if (infoStr != null) ps.setString(14, infoStr) else ps.setNull(14, Types.VARCHAR)
                        ps.setLong(15, grense.versjon())
                        ps.setInt(16, grense.versjonid())
                        val oppStr = grense.oppdatertav()
                        if (oppStr != null) ps.setString(17, oppStr) else ps.setNull(17, Types.VARCHAR)
                        ps.addBatch()
                        imported++

                        // Check if this ID already exists
                        val id = entry.id()
                        val exists = conn.prepareStatement(
                            "SELECT 1 FROM nibas_arbeidsliste_schema.matrikkel_grenselinje WHERE id = ?"
                        ).use { checkStmt ->
                            checkStmt.setLong(1, id)
                            checkStmt.executeQuery().next()
                        }

                        if (exists) {
                            println("WARNING: Duplicate ID ${id} found in file=${file.name}")
                            // Log the duplicate to our temp table
                            conn.prepareStatement("INSERT INTO temp_duplicate_ids (id) VALUES (?)").use { dupStmt ->
                                dupStmt.setLong(1, id)
                                dupStmt.execute()
                            }
                        } else {
                            println("Queued grenselinje id=${id} from file=${file.name}")
                        }
                    }
                }
            }
            val insertedCount = ps.executeBatch().sum()
            println("Processed $imported grenselinjer, inserted $insertedCount new records.")

            // Log any duplicates we found
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("""
                    SELECT id, COUNT(*) as cnt
                    FROM temp_duplicate_ids
                    GROUP BY id
                    ORDER BY cnt DESC
                    LIMIT 10
                """.trimIndent())

                if (rs.next()) {
                    println("\n=== DUPLICATE IDS FOUND ===")
                    println("ID\tOCCURRENCES")
                    do {
                        println("${rs.getLong(1)}\t${rs.getInt(2)}")
                    } while (rs.next())
                    println("==========================")
                } else {
                    println("No duplicate IDs found in this import batch.")
                }
            }
        }

        endringsnummer?.let { enr ->
            conn.prepareStatement(
                "INSERT INTO nibas_arbeidsliste_schema.matrikkel_endringsnummer(endringsnummer, oppdateringsdato) " +
                    "VALUES (?, CURRENT_TIMESTAMP) ON CONFLICT (endringsnummer) DO NOTHING"
            ).use { ps ->
                ps.setLong(1, enr)
                ps.executeUpdate()
                println("Saved endringsnummer: $enr to database")
            }
        }

        connector.commit()
    }
}

fun main() {
    // Extract directory and potentially the endringsnummer from the path
    // Try to extract endringsnummer from the directory name if it follows the pattern M22-DATA-{endringsnummer}
    val endringsnummer = 364590149L
    val dirPath = File("M22-DATA-${endringsnummer}").canonicalFile

    importConvertedGrenselinjer(dirPath, endringsnummer)
}
