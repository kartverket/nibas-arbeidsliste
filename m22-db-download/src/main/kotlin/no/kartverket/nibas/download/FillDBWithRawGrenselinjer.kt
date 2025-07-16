package no.kartverket.nibas.download

import java.math.BigDecimal
import java.sql.Connection
import java.sql.Struct
import java.sql.Types

/**
 * Import raw grenselinjer data directly from M22 Oracle database to PostgreSQL.
 */
fun importRawGrenselinjer(matrikkelConnection: Connection) {
    println("Starting raw grenselinjer import...")

    ArbeidslisteDbConnector().use { connector ->
        val targetConnection = connector.connection

        // Gets Kommunegrense, Fylkesgrense, Riksgrense and Territorialgrense
        val oracleQuery = """
            SELECT
           id,
           hjelpelinjetypeid,
           omtvistet,
           folgerterrengdetaljid,
           administrativgrensekodeid,
           malemetodeid,
           noyaktighet,
           datafangstdato,
           informasjon,
           lagretnoyaktighetsklasse,
           kommunenrstrengcache,
           versjon,
           kurvesegmenttype,
           kurvekoordinatsystemkode,
           kurvestartpunktid,
           kurveendpunktid,
           kurvebuepunktx,
           kurvebuepunkty,
           kurvepositions,
           oppdateringsdato,
           oppdatertav,
           versjonid                  
           FROM grenselinje
           WHERE administrativgrensekodeid > 0
           ORDER BY id
        """.trimIndent()

        val insertSql = """
            INSERT INTO nibas_arbeidsliste_schema.raw_matrikkel_grenselinje (
                id, hjelpelinjetypeid, omtvistet, folgerterrengdetaljid,
                administrativgrensekodeid, malemetodeid, noyaktighet, datafangstdato,
                informasjon, lagretnoyaktighetsklasse, kommunenrstrengcache, versjon,
                kurvesegmenttype, kurvekoordinatsystemkode, kurvestartpunktid, kurveendpunktid,
                kurvebuepunktx, kurvebuepunkty, kurvepositions, oppdateringsdato,
                oppdatertav, versjonid
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """.trimIndent()

        matrikkelConnection.createStatement().use { stmt ->
            stmt.fetchSize = 10000
            val rs = stmt.executeQuery(oracleQuery)

            targetConnection.prepareStatement(insertSql).use { ps ->
                var count = 0
                var batchCount = 0

                while (rs.next()) {
                    ps.setLong(1, rs.getLong("id"))

                    val hjelpelinjetypeid = rs.getInt("hjelpelinjetypeid")
                    if (rs.wasNull()) ps.setNull(2, Types.INTEGER) else ps.setInt(2, hjelpelinjetypeid)

                    val omtvistet = rs.getBoolean("omtvistet")
                    if (rs.wasNull()) ps.setNull(3, Types.BOOLEAN) else ps.setBoolean(3, omtvistet)

                    val folgerterrengdetaljid = rs.getInt("folgerterrengdetaljid")
                    if (rs.wasNull()) ps.setNull(4, Types.INTEGER) else ps.setInt(4, folgerterrengdetaljid)

                    val administrativgrensekodeid = rs.getInt("administrativgrensekodeid")
                    if (rs.wasNull()) ps.setNull(5, Types.INTEGER) else ps.setInt(5, administrativgrensekodeid)

                    val malemetodeid = rs.getInt("malemetodeid")
                    if (rs.wasNull()) ps.setNull(6, Types.INTEGER) else ps.setInt(6, malemetodeid)

                    val noyaktighet = rs.getInt("noyaktighet")
                    if (rs.wasNull()) ps.setNull(7, Types.INTEGER) else ps.setInt(7, noyaktighet)

                    val datafangstdato = rs.getDate("datafangstdato")
                    if (datafangstdato != null) ps.setDate(8, datafangstdato) else ps.setNull(8, Types.DATE)

                    val informasjon = rs.getString("informasjon")
                    if (informasjon != null) ps.setString(9, informasjon) else ps.setNull(9, Types.VARCHAR)

                    val lagretnoyaktighetsklasse = rs.getInt("lagretnoyaktighetsklasse")
                    if (rs.wasNull()) ps.setNull(10, Types.INTEGER) else ps.setInt(10, lagretnoyaktighetsklasse)

                    val kommunenrstrengcache = rs.getString("kommunenrstrengcache")
                    if (kommunenrstrengcache != null) ps.setString(11, kommunenrstrengcache) else ps.setNull(11, Types.VARCHAR)

                    val versjon = rs.getLong("versjon")
                    if (rs.wasNull()) ps.setNull(12, Types.BIGINT) else ps.setLong(12, versjon)

                    val kurvesegmenttype = rs.getString("kurvesegmenttype")
                    if (kurvesegmenttype != null) ps.setString(13, kurvesegmenttype) else ps.setNull(13, Types.VARCHAR)

                    val kurvekoordinatsystemkode = rs.getInt("kurvekoordinatsystemkode")
                    if (rs.wasNull()) ps.setNull(14, Types.INTEGER) else ps.setInt(14, kurvekoordinatsystemkode)

                    val kurvestartpunktid = rs.getLong("kurvestartpunktid")
                    if (rs.wasNull()) ps.setNull(15, Types.BIGINT) else ps.setLong(15, kurvestartpunktid)

                    val kurveendpunktid = rs.getLong("kurveendpunktid")
                    if (rs.wasNull()) ps.setNull(16, Types.BIGINT) else ps.setLong(16, kurveendpunktid)

                    val kurvebuepunktx = rs.getDouble("kurvebuepunktx")
                    if (rs.wasNull()) ps.setNull(17, Types.DOUBLE) else ps.setDouble(17, kurvebuepunktx)

                    val kurvebuepunkty = rs.getDouble("kurvebuepunkty")
                    if (rs.wasNull()) ps.setNull(18, Types.DOUBLE) else ps.setDouble(18, kurvebuepunkty)

                    val kurvepositions = try {
                        val structObj = rs.getObject("kurvepositions", Struct::class.java)
                        if (structObj != null) {
                            val structAttrs = structObj.attributes

                            @Suppress("UNCHECKED_CAST")
                            val array = (structAttrs[1] as java.sql.Array).getArray(mapOf("NUMBER" to BigDecimal::class.java)) as Array<BigDecimal>

                            /*
                            Oracle stores coordinates as:
                            [X1, Y1, Z1, X2, Y2, Z2, X3, Y3, Z3, ...] (with Z = elevation)
                            We do not need Z. So:
                            We want to store as JSON:
                            [[X1,Y1], [X2,Y2], [X3,Y3], ...] (only X,Y coordinates)
                             */

                            if (array.isNotEmpty()) {
                                // Convert to JSON array of coordinates: [[x1,y1], [x2,y2], ...]
                                val coordsList = mutableListOf<String>()
                                // Iterate over the array in steps of 3 (X, Y, Z)
                                for (i in array.indices step 3) {
                                    val x = array[i].toDouble()
                                    val y = array[i + 1].toDouble()
                                    coordsList.add("[$x,$y]")
                                }

                                "[${coordsList.joinToString(",")}]"
                            } else {
                                null
                            }
                        } else null
                    } catch (e: Exception) {
                        println("Warning: Failed to parse kurvepositions for grenselinje ${rs.getLong("id")}: ${e.message}")
                        null
                    }
                    if (kurvepositions != null) ps.setString(19, kurvepositions) else ps.setNull(19, Types.VARCHAR)

                    val oppdateringsdato = rs.getTimestamp("oppdateringsdato")
                    if (oppdateringsdato != null) ps.setTimestamp(20, oppdateringsdato) else ps.setNull(20, Types.TIMESTAMP)

                    val oppdatertav = rs.getString("oppdatertav")
                    if (oppdatertav != null) ps.setString(21, oppdatertav) else ps.setNull(21, Types.VARCHAR)

                    val versjonid = rs.getLong("versjonid")
                    if (rs.wasNull()) ps.setNull(22, Types.BIGINT) else ps.setLong(22, versjonid)

                    ps.addBatch()
                    batchCount++
                    count++

                    if (batchCount >= 10000) {
                        ps.executeBatch()
                        println("Imported $count grenselinjer so far...")
                        batchCount = 0
                    }
                }

                if (batchCount > 0) {
                    ps.executeBatch()
                }

                println("Successfully imported $count raw grenselinjer records")
            }
        }

        connector.commit()
        println("Raw grenselinjer import completed successfully!")
    }
}
