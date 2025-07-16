package no.kartverket.nibas.download

import java.sql.Connection
import java.sql.Types

/**
 * Import raw grensepunkt data directly from an M22 Oracle database to PostgreSQL.
 */
fun importRawGrensepunkt(matrikkelConnection: Connection) {
    println("Starting raw grensepunkt import...")

    ArbeidslisteDbConnector().use { connector ->
        val targetConnection = connector.connection

        // Only get grensepunkt used in grenselinje
        val oracleQuery = """
            SELECT DISTINCT
                gp.id,
                gp.positionx,
                gp.positiony,
                gp.koordinatsystemkodeid,
                gp.grensemerkenedsattiid,
                gp.grensepunkttypeid,
                gp.malemetodeid,
                gp.noyaktighet,
                gp.datafangstdato,
                gp.grensepunktnr,
                gp.kommunenrstrengcache,
                gp.versjon,
                gp.oppdateringsdato,
                gp.oppdatertav,
                gp.versjonid,
                gp.uuid
            FROM grensepunkt gp
            WHERE gp.id IN (
                SELECT kurvestartpunktid FROM grenselinje
                WHERE kurvestartpunktid IS NOT NULL
                AND administrativgrensekodeid > 0
                UNION
                SELECT kurveendpunktid FROM grenselinje
                WHERE kurveendpunktid IS NOT NULL
                AND administrativgrensekodeid > 0
            )
            ORDER BY gp.id
        """.trimIndent()

        val insertSql = """
            INSERT INTO nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt (
                id, positionx, positiony, koordinatsystemkodeid,
                grensemerkenedsattiid, grensepunkttypeid, malemetodeid,
                noyaktighet, datafangstdato, grensepunktnr, kommunenrstrengcache,
                versjon, oppdateringsdato, oppdatertav, versjonid, uuid
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO NOTHING
        """.trimIndent()

        matrikkelConnection.createStatement().use { stmt ->
            stmt.fetchSize = 10000
            val rs = stmt.executeQuery(oracleQuery)

            targetConnection.prepareStatement(insertSql).use { ps ->
                var count = 0
                var batchCount = 0

                while (rs.next()) {
                    // Raw data from M22 Oracle DB
                    ps.setLong(1, rs.getLong("id"))
                    ps.setDouble(2, rs.getDouble("positionx"))
                    ps.setDouble(3, rs.getDouble("positiony"))
                    ps.setInt(4, rs.getInt("koordinatsystemkodeid"))

                    val grensemerkeId = rs.getInt("grensemerkenedsattiid")
                    if (rs.wasNull()) ps.setNull(5, Types.INTEGER) else ps.setInt(5, grensemerkeId)

                    val grensepunkttypeId = rs.getInt("grensepunkttypeid")
                    if (rs.wasNull()) ps.setNull(6, Types.INTEGER) else ps.setInt(6, grensepunkttypeId)

                    val malemetodeId = rs.getInt("malemetodeid")
                    if (rs.wasNull()) ps.setNull(7, Types.INTEGER) else ps.setInt(7, malemetodeId)

                    val noyaktighet = rs.getInt("noyaktighet")
                    if (rs.wasNull()) ps.setNull(8, Types.INTEGER) else ps.setInt(8, noyaktighet)

                    val datafangstdato = rs.getDate("datafangstdato")
                    if (datafangstdato != null) ps.setDate(9, datafangstdato) else ps.setNull(9, Types.DATE)

                    val grensepunktnr = rs.getString("grensepunktnr")
                    if (grensepunktnr != null) ps.setString(10, grensepunktnr) else ps.setNull(10, Types.VARCHAR)

                    val kommunenrstrengcache = rs.getString("kommunenrstrengcache")
                    if (kommunenrstrengcache != null) ps.setString(11, kommunenrstrengcache) else ps.setNull(11, Types.VARCHAR)

                    val versjon = rs.getLong("versjon")
                    if (rs.wasNull()) ps.setNull(12, Types.BIGINT) else ps.setLong(12, versjon)

                    val oppdateringsdato = rs.getTimestamp("oppdateringsdato")
                    if (oppdateringsdato != null) ps.setTimestamp(13, oppdateringsdato) else ps.setNull(13, Types.TIMESTAMP)

                    val oppdatertav = rs.getString("oppdatertav")
                    if (oppdatertav != null) ps.setString(14, oppdatertav) else ps.setNull(14, Types.VARCHAR)

                    val versjonid = rs.getLong("versjonid")
                    if (rs.wasNull()) ps.setNull(15, Types.BIGINT) else ps.setLong(15, versjonid)

                    val uuid = rs.getString("uuid")
                    if (uuid != null) ps.setString(16, uuid) else ps.setNull(16, Types.VARCHAR)

                    ps.addBatch()
                    batchCount++
                    count++

                    if (batchCount >= 10000) {
                        ps.executeBatch()
                        println("Imported $count grensepunkt so far...")
                        batchCount = 0
                    }
                }

                if (batchCount > 0) {
                    ps.executeBatch()
                }

                println("Successfully imported $count raw grensepunkt records")
            }
        }

        connector.commit()
        println("Raw grensepunkt import completed successfully!")
    }
}
