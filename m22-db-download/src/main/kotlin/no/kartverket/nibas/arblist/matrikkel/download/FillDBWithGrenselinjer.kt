package no.kartverket.nibas.arblist.matrikkel.download

import no.kartverket.nibas.arblist.matrikkel.download.convert.ConvertedGrenselinjeFile
import java.io.File
import java.sql.Types
import java.sql.Date
import java.sql.Timestamp

fun importConvertedGrenselinjer(convertedDir: File) {
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
        conn.prepareStatement(
            "INSERT INTO nibas_arbeidsliste_schema.matrikkel_grenselinje(" +
                "id, hjelpelinjetype_id, omtvistet, folgerterrengdetalj_id, administrativgrensekode_id, " +
                "malemetode_id, noyaktighet, datafangstdato, lagretnoyaktighetsklasse, geom, oppdateringsdato, " +
                "kommunenrstrengcache, informasjoncache, versjon, versjon_id, oppdatert_av" +
                ") VALUES (?,?,?,?,?,?,?,?,?,ST_GeomFromText(?, 25833),?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING"
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
                        val knrStr = grense.kommunenrstrengcache()
                        if (knrStr != null) ps.setString(12, knrStr) else ps.setNull(12, Types.VARCHAR)
                        val infoStr = grense.informasjon()
                        if (infoStr != null) ps.setString(13, infoStr) else ps.setNull(13, Types.VARCHAR)
                        ps.setLong(14, grense.versjon())
                        ps.setInt(15, grense.versjonid())
                        val oppStr = grense.oppdatertav()
                        if (oppStr != null) ps.setString(16, oppStr) else ps.setNull(16, Types.VARCHAR)
                        ps.addBatch()
                        imported++
                        println("Queued grenselinje id=${entry.id()} from file=${file.name}")
                    }
                }
            }
            ps.executeBatch()
            println("Imported $imported grenselinjer from converted files.")
        }
        connector.commit()
    }
}

fun main() {
    val convertedDir = File("/home/haugkr/IdeaProjects/smia/nibas/nibas-arbeidsliste/m22-db-download/M22-DATA-364590149")
    importConvertedGrenselinjer(convertedDir)
}
