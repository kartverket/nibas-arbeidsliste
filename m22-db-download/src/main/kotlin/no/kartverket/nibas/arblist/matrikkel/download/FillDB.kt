package no.kartverket.nibas.arblist.matrikkel.download

import no.kartverket.nibas.arblist.matrikkel.download.convert.readFiles
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Grensepunkt
import no.kartverket.nibas.flatbuffer.MatrikkelDB.GrensepunktBuffer
import no.kartverket.nibas.arblist.matrikkel.download.ArbeidslisteDbConnector
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Koordinat
import no.kartverket.nibas.flatbuffer.MatrikkelDB.Posisjonskvalitet
import java.sql.Date
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.io.File

fun readGrensepunktFromFlatBuffer(
    grensepunktFiles: Iterable<File>
) {
    ArbeidslisteDbConnector().use { connector ->
        val conn = connector.connection
        conn.prepareStatement(
            "INSERT INTO nibas_arbeidsliste_schema.matrikkel_grensepunkt(" +
                "id, position_x, position_y, koordinatsystemkode_id, " +
                "grensemerke_nedsatt_i_id, grensepunkttype_id, malemetode_id, " +
                "noyaktighet, datafangstdato, grensepunktnr, oppdateringsdato" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING"
        ).use { ps ->
            readFiles(grensepunktFiles, GrensepunktBuffer::getRootAsGrensepunktBuffer) { buf, _ ->
                val entry = Grensepunkt()
                // take up to 10 entries for testing
                val limit = maxOf(10, buf.grensepunkterLength())
                for (i in 0 until limit) {
                    buf.grensepunkter(entry, i)
                    // extract all fields
                    val id = entry.id()
                    val coord = entry.koordinat(Koordinat())
                    val x = coord.x().toDouble()
                    val y = coord.y().toDouble()
                    val sysKode = entry.koordinatsystemKode().toInt()
                    val nedKode = entry.grensemerkeNedsattIKode().toInt()
                    // no type in flatbuffer
                    // use null for grensepunkttype
                    val mal = entry.posisjonskvalitet(Posisjonskvalitet()).maalemetodeKode().toInt()
                    val noy = entry.posisjonskvalitet(Posisjonskvalitet()).noyaktighet()
                    // bind parameters
                    ps.setLong(1, id)
                    ps.setDouble(2, x)
                    ps.setDouble(3, y)
                    ps.setInt(4, sysKode)
                    ps.setInt(5, nedKode)
                    ps.setNull(6, Types.SMALLINT)
                    ps.setInt(7, mal)
                    ps.setInt(8, noy)
                    // bind datafangstdato or NULL
                    val dfStruct = entry.datafangstdato()
                    if (dfStruct != null) {
                        val dt = Date.valueOf(
                            LocalDate.of(
                                dfStruct.year(), dfStruct.month().toInt(), dfStruct.day().toInt()
                            )
                        )
                        ps.setDate(9, dt)
                    } else {
                        ps.setNull(9, Types.DATE)
                    }
                    // bind grensepunktnr (nullable)
                    ps.setString(10, entry.grensepunktnr())
                    // bind oppdateringsdato or NULL
                    val updStruct = entry.oppdateringsdato()
                    if (updStruct != null) {
                        val ts = Timestamp.from(
                            Instant.ofEpochSecond(updStruct.seconds(), updStruct.nanos().toLong())
                        )
                        ps.setTimestamp(11, ts)
                    } else {
                        ps.setNull(11, Types.TIMESTAMP)
                    }
                    println("Queued id=$id")
                    println("Queued x=$x y=$y sysKode=$sysKode nedKode=$nedKode mal=$mal noy=$noy")
                    println("Queued grensepunktnr=${entry.grensepunktnr()}")
                    ps.addBatch()
                }
            }
            ps.executeBatch()
        }
        connector.commit()
    }
}

fun main() {
    val dataDir = File("/home/haugkr/IdeaProjects/smia/nibas/nibas-arbeidsliste/M22-DATA-364590149")

    // pick up every .fb file in there (and sort if you care about order)
    val files = dataDir.listFiles { f ->
        f.isFile
            && f.extension.equals("fb", ignoreCase = true)
            && f.name.contains("grensepunkt", ignoreCase = true)
    }?.sortedBy { it.name }?.toList()
        ?: emptyList()

    // now read just the first 10 ids from each .fb
    readGrensepunktFromFlatBuffer(files)
}
