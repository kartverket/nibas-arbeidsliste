@file:Suppress("LoggingSimilarMessage")

package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import no.kartverket.nibas.matrikkel.api.domain.MatrikkelContext
import no.kartverket.nibas.matrikkel.api.domain.geometri.Arc
import no.kartverket.nibas.matrikkel.api.domain.geometri.Polyline
import no.kartverket.nibas.matrikkel.api.domain.kommune.Kommune
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Matrikkelenhet
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.MatrikkelenhetIdList
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Teiggrense
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.TeiggrenseId
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.Teiggrensepunkt
import no.kartverket.nibas.matrikkel.api.domain.matrikkelenhet.TeiggrensepunktId
import no.kartverket.nibas.matrikkel.api.service.matrikkelenhet.MatrikkelenhetService
import no.kartverket.nibas.matrikkel.api.service.store.StoreService
import no.kartvkerket.nibas.arbeidsliste.matrikkel.changelog.BubbleWrapper
import no.kartvkerket.nibas.arbeidsliste.matrikkel.changelog.ChangeSet
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Date
import java.sql.Timestamp

/**
 * Converts ChangeSet objects from MatrikkelChangelog to raw database tables.
 */
@Service
class ChangeSetProcessor(
    private val jdbcTemplate: JdbcTemplate,
    private val matrikkelenhetService: MatrikkelenhetService,
    private val storeService: StoreService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ChangeSetProcessor::class.java)
    }


    @Transactional
    fun processChangeSet(
        changeSet: ChangeSet,
        matrikkelContext: MatrikkelContext,
        kommuneMap: Map<Int, Kommune>
    ): Int {
        var processedCount = 0

        log.info("Processing ChangeSet with {} boundary lines, {} boundary points, {} deleted lines, {} deleted points",
            changeSet.changedBoundaryLines.size, changeSet.changedBoundaryPoints.size,
            changeSet.deletedBoundaryLineIds.size, changeSet.deletedBoundaryPointIds.size)

        changeSet.deletedBoundaryLineIds.forEach { id ->
            log.info("Processing SLETTING for Teiggrense id={}", id.value)
            deleteTeiggrense(id.value)
        }
        changeSet.deletedBoundaryPointIds.forEach { id ->
            log.info("Processing SLETTING for Teiggrensepunkt id={}", id.value)
            deleteTeiggrensepunkt(id.value)
        }

        changeSet.changedBoundaryPoints.forEach { (_, wrapper) ->
            processGrensepunkt(wrapper, matrikkelContext, kommuneMap)
        }

        changeSet.changedBoundaryLines.forEach { wrapper ->
            processGrenselinje(wrapper, matrikkelContext, kommuneMap)
            processedCount++
        }

        log.info("Processed {} boundary lines from ChangeSet", processedCount)
        return processedCount
    }

    private fun processGrensepunkt(
        wrapper: BubbleWrapper<Teiggrensepunkt>,
        matrikkelContext: MatrikkelContext,
        kommuneMap: Map<Int, Kommune>
    ) {
        val punkt = wrapper.bubble

        if (punkt.sluttdato?.timestamp != null) {
            log.info("Deleting Teiggrensepunkt id={} (sluttdato={})", punkt.id?.value, punkt.sluttdato?.timestamp)
            deleteTeiggrensepunkt(punkt.id?.value!!)
            return
        }

        val punktKommune = lookupKommuneForPoint(punkt.id?.value!!, matrikkelContext, kommuneMap)

        try {
            val existing = jdbcTemplate.queryForMap(
                """SELECT positionx, positiony, koordinatsystemkodeid, grensemerkenedsattiid,
                   grensepunkttypeid, malemetodeid, noyaktighet, datafangstdato, kommunenrstrengcache,
                   oppdateringsdato, oppdatertav, versjonid, uuid FROM nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt WHERE id = ?""",
                punkt.id?.value
            )
            log.info("=== TEIGGRENSEPUNKT UPDATE COMPARISON id={} ===", punkt.id?.value)
            logFieldComparison("positionx", existing["positionx"], punkt.posisjon?.x)
            logFieldComparison("positiony", existing["positiony"], punkt.posisjon?.y)
            logFieldComparison("koordinatsystemkodeid", existing["koordinatsystemkodeid"], punkt.koordinatsystemKodeId?.value)
            logFieldComparison("grensemerkenedsattiid", existing["grensemerkenedsattiid"], punkt.grensemerkeNedsattIId?.value)
            logFieldComparison("grensepunkttypeid", existing["grensepunkttypeid"], punkt.grensepunkttypeId?.value)
            logFieldComparison("malemetodeid", existing["malemetodeid"], punkt.kvalitet?.malemetodeId?.value)
            logFieldComparison("noyaktighet", existing["noyaktighet"], punkt.kvalitet?.noyaktighet)
            logFieldComparison("datafangstdato", existing["datafangstdato"],
                punkt.datafangstdato?.date?.toGregorianCalendar()?.time?.let { Date(it.time) })
            logFieldComparison("kommunenrstrengcache", existing["kommunenrstrengcache"], punktKommune)
            logFieldComparison("oppdateringsdato", existing["oppdateringsdato"],
                punkt.oppdateringsdato?.timestamp?.toGregorianCalendar()?.time?.let { Timestamp(it.time) })
            logFieldComparison("oppdatertav", existing["oppdatertav"], punkt.oppdatertAv)
            logFieldComparison("versjonid", existing["versjonid"], punkt.versjonId)
            logFieldComparison("uuid", existing["uuid"], punkt.uuid?.uuid)
            log.info("=== END COMPARISON ===")
        } catch (_: Exception) {
            log.info("NEW INSERT - Teiggrensepunkt id={}: coords=({},{}), uuid={}",
                punkt.id?.value, punkt.posisjon?.x, punkt.posisjon?.y, punkt.uuid?.uuid)
        }

        jdbcTemplate.update(
            """
            INSERT INTO nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt (
                id, positionx, positiony, koordinatsystemkodeid,
                grensemerkenedsattiid, grensepunkttypeid, malemetodeid,
                noyaktighet, datafangstdato, grensepunktnr, kommunenrstrengcache,
                versjon, oppdateringsdato, oppdatertav, versjonid, uuid
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                positionx = EXCLUDED.positionx,
                positiony = EXCLUDED.positiony,
                koordinatsystemkodeid = EXCLUDED.koordinatsystemkodeid,
                grensemerkenedsattiid = EXCLUDED.grensemerkenedsattiid,
                grensepunkttypeid = EXCLUDED.grensepunkttypeid,
                malemetodeid = EXCLUDED.malemetodeid,
                noyaktighet = EXCLUDED.noyaktighet,
                datafangstdato = EXCLUDED.datafangstdato,
                grensepunktnr = EXCLUDED.grensepunktnr,
                kommunenrstrengcache = EXCLUDED.kommunenrstrengcache,
                versjon = EXCLUDED.versjon,
                oppdateringsdato = EXCLUDED.oppdateringsdato,
                oppdatertav = EXCLUDED.oppdatertav,
                versjonid = EXCLUDED.versjonid,
                uuid = EXCLUDED.uuid
            """,
            punkt.id?.value,                                                    // id
            punkt.posisjon?.x,                                               // positionx
            punkt.posisjon?.y,                                               // positiony
            punkt.koordinatsystemKodeId?.value,                              // koordinatsystemkodeid
            punkt.grensemerkeNedsattIId?.value,                              // grensemerkenedsattiid
            punkt.grensepunkttypeId?.value,                                  // grensepunkttypeid
            punkt.kvalitet?.malemetodeId?.value,                             // malemetodeid
            punkt.kvalitet?.noyaktighet,                                     // noyaktighet
            punkt.datafangstdato?.date?.toGregorianCalendar()?.time?.let {
                Date(it.time)
            },   // datafangstdato
            null,                                                            // grensepunktnr - Oracle only
            punktKommune,                                                    // kommunenrstrengcache
            null,                                                            // versjon - Oracle only
            punkt.oppdateringsdato?.timestamp?.toGregorianCalendar()?.time?.let {
                Timestamp(it.time)
            },  // oppdateringsdato
            punkt.oppdatertAv,                                               // oppdatertav
            punkt.versjonId,                                                 // versjonid
            punkt.uuid?.uuid                                                 // uuid
        )
    }

    private fun processGrenselinje(
        wrapper: BubbleWrapper<Teiggrense>,
        matrikkelContext: MatrikkelContext,
        kommuneMap: Map<Int, Kommune>
    ) {
        val grense = wrapper.bubble

        val kurvepositions = when (val kurve = grense.kurve) {
            is Polyline -> {
                val positions = kurve.kurvepunkter?.item ?: emptyList()
                if (positions.isNotEmpty()) {
                    val coordsList = positions.map { pos -> "[${pos.x},${pos.y}]" }
                    "[${coordsList.joinToString(",")}]"
                } else null
            }

            is Arc -> {
                val buepunkt = kurve.buepunkt
                if (buepunkt != null) {
                    "[[${buepunkt.x},${buepunkt.y}]]"
                } else null
            }

            else -> null
        }

        if (grense.sluttdato?.timestamp != null) {
            log.info("Deleting Teiggrense id={} (sluttdato={})", grense.id?.value, grense.sluttdato?.timestamp)
            deleteTeiggrense(grense.id?.value!!)
            return
        }

        val grenseKommune = lookupKommuneForLine(grense.id?.value!!, matrikkelContext, kommuneMap)

        try {
            val existing = jdbcTemplate.queryForMap(
                """SELECT hjelpelinjetypeid, omtvistet, folgerterrengdetaljid, administrativgrensekodeid,
                   malemetodeid, noyaktighet, datafangstdato, lagretnoyaktighetsklasse, kommunenrstrengcache, kurvesegmenttype,
                   kurvekoordinatsystemkode, kurvestartpunktid, kurveendpunktid, kurvebuepunktx, kurvebuepunkty,
                   kurvepositions, oppdateringsdato, oppdatertav, versjonid FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje WHERE id = ?""",
                grense.id?.value
            )
            log.info("=== TEIGGRENSE UPDATE COMPARISON id={} ===", grense.id?.value)
            logFieldComparison("hjelpelinjetypeid", existing["hjelpelinjetypeid"], grense.hjelpelinjetypeId?.value)
            logFieldComparison("omtvistet", existing["omtvistet"], grense.isOmtvistet)
            logFieldComparison("folgerterrengdetaljid", existing["folgerterrengdetaljid"], grense.folgerTerrengdetaljId?.value)
            logFieldComparison("administrativgrensekodeid", existing["administrativgrensekodeid"], grense.administrativGrenseKodeId?.value)
            logFieldComparison("malemetodeid", existing["malemetodeid"], grense.kvalitet?.malemetodeId?.value)
            logFieldComparison("noyaktighet", existing["noyaktighet"], grense.kvalitet?.noyaktighet)
            logFieldComparison("datafangstdato", existing["datafangstdato"],
                grense.datafangstdato?.date?.toGregorianCalendar()?.time?.let { Date(it.time) })
            logFieldComparison("lagretnoyaktighetsklasse", existing["lagretnoyaktighetsklasse"], grense.lagretNoyaktighetsklasseId?.value)
            logFieldComparison("kommunenrstrengcache", existing["kommunenrstrengcache"], grenseKommune)
            logFieldComparison("kurvesegmenttype", existing["kurvesegmenttype"], when (grense.kurve) {
                is Polyline -> "polyline"
                is Arc -> "arc"
                else -> "unknown"
            })
            logFieldComparison("kurvekoordinatsystemkode", existing["kurvekoordinatsystemkode"], grense.kurve?.koordinatsystemKodeId?.value)
            logFieldComparison("kurvestartpunktid", existing["kurvestartpunktid"], grense.kurve?.startpunktId?.value)
            logFieldComparison("kurveendpunktid", existing["kurveendpunktid"], grense.kurve?.endpunktId?.value)
            logFieldComparison("kurvebuepunktx", existing["kurvebuepunktx"], if (grense.kurve is Arc) (grense.kurve as Arc).buepunkt?.x else null)
            logFieldComparison("kurvebuepunkty", existing["kurvebuepunkty"], if (grense.kurve is Arc) (grense.kurve as Arc).buepunkt?.y else null)
            logFieldComparison("kurvepositions", existing["kurvepositions"], kurvepositions)
            logFieldComparison("oppdateringsdato", existing["oppdateringsdato"],
                grense.oppdateringsdato?.timestamp?.toGregorianCalendar()?.time?.let { Timestamp(it.time) })
            logFieldComparison("oppdatertav", existing["oppdatertav"], grense.oppdatertAv)
            logFieldComparison("versjonid", existing["versjonid"], grense.versjonId)
            log.info("=== END COMPARISON ===")
        } catch (_: Exception) {
            log.info("NEW INSERT - Teiggrense id={}: kurvepositions={}", grense.id?.value, kurvepositions)
        }

        jdbcTemplate.update(
            """
            INSERT INTO nibas_arbeidsliste_schema.raw_matrikkel_grenselinje (
                id, hjelpelinjetypeid, omtvistet, folgerterrengdetaljid,
                administrativgrensekodeid, malemetodeid, noyaktighet, datafangstdato,
                informasjon, lagretnoyaktighetsklasse, kommunenrstrengcache, versjon,
                kurvesegmenttype, kurvekoordinatsystemkode, kurvestartpunktid, kurveendpunktid,
                kurvebuepunktx, kurvebuepunkty, kurvepositions, oppdateringsdato,
                oppdatertav, versjonid
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                hjelpelinjetypeid = EXCLUDED.hjelpelinjetypeid,
                omtvistet = EXCLUDED.omtvistet,
                folgerterrengdetaljid = EXCLUDED.folgerterrengdetaljid,
                administrativgrensekodeid = EXCLUDED.administrativgrensekodeid,
                malemetodeid = EXCLUDED.malemetodeid,
                noyaktighet = EXCLUDED.noyaktighet,
                datafangstdato = EXCLUDED.datafangstdato,
                informasjon = EXCLUDED.informasjon,
                lagretnoyaktighetsklasse = EXCLUDED.lagretnoyaktighetsklasse,
                kommunenrstrengcache = EXCLUDED.kommunenrstrengcache,
                versjon = EXCLUDED.versjon,
                kurvesegmenttype = EXCLUDED.kurvesegmenttype,
                kurvekoordinatsystemkode = EXCLUDED.kurvekoordinatsystemkode,
                kurvestartpunktid = EXCLUDED.kurvestartpunktid,
                kurveendpunktid = EXCLUDED.kurveendpunktid,
                kurvebuepunktx = EXCLUDED.kurvebuepunktx,
                kurvebuepunkty = EXCLUDED.kurvebuepunkty,
                kurvepositions = EXCLUDED.kurvepositions,
                oppdateringsdato = EXCLUDED.oppdateringsdato,
                oppdatertav = EXCLUDED.oppdatertav,
                versjonid = EXCLUDED.versjonid
            """,
            grense.id?.value,                                                   // id
            grense.hjelpelinjetypeId?.value,                                     // hjelpelinjetypeid
            grense.isOmtvistet,                                                  // omtvistet
            grense.folgerTerrengdetaljId?.value,                                 // folgerterrengdetaljid
            grense.administrativGrenseKodeId?.value,                             // administrativgrensekodeid
            grense.kvalitet?.malemetodeId?.value,                                // malemetodeid
            grense.kvalitet?.noyaktighet,                                        // noyaktighet
            grense.datafangstdato?.date?.toGregorianCalendar()?.time?.let {
                Date(it.time)
            },     // datafangstdato
            null,                                                                // informasjon
            grense.lagretNoyaktighetsklasseId?.value,                            // lagretnoyaktighetsklasse
            grenseKommune,                                                       // kommunenrstrengcache
            null,                                                                // versjon
            when (grense.kurve) {                                                // kurvesegmenttype
                is Polyline -> "polyline"
                is Arc -> "arc"
                else -> "unknown"
            },
            grense.kurve?.koordinatsystemKodeId?.value,                          // kurvekoordinatsystemkode
            grense.kurve?.startpunktId?.value,                                   // kurvestartpunktid
            grense.kurve?.endpunktId?.value,                                     // kurveendpunktid
            if (grense.kurve is Arc) (grense.kurve as Arc).buepunkt?.x else null, // kurvebuepunktx
            if (grense.kurve is Arc) (grense.kurve as Arc).buepunkt?.y else null, // kurvebuepunkty
            kurvepositions,                                                      // kurvepositions
            grense.oppdateringsdato?.timestamp?.toGregorianCalendar()?.time?.let {
                Timestamp(it.time)
            },     // oppdateringsdato
            grense.oppdatertAv,                                                  // oppdatertav
            grense.versjonId                                                     // versjonid
        )
    }


    private fun deleteTeiggrensepunkt(id: Long) {
        val rowsDeleted = jdbcTemplate.update(
            "DELETE FROM nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt WHERE id = ?",
            id
        )
        log.info("Deleted {} rows from raw_matrikkel_grensepunkt for id={}", rowsDeleted, id)
    }


    private fun deleteTeiggrense(id: Long) {
        val rowsDeleted = jdbcTemplate.update(
            "DELETE FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje WHERE id = ?",
            id
        )
        log.info("Deleted {} rows from raw_matrikkel_grenselinje for id={}", rowsDeleted, id)
    }

    private fun logFieldComparison(fieldName: String, oldValue: Any?, newValue: Any?) {
        val normalizedOld = normalizeValue(oldValue)
        val normalizedNew = normalizeValue(newValue)

        if (normalizedOld != normalizedNew) {
            log.info("  CHANGED {} : [{}] -> [{}]", fieldName, normalizedOld, normalizedNew)
        } else {
            log.info("  SAME    {} : [{}]", fieldName, normalizedOld)
        }
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            is Number -> value.toLong()
            else -> value
        }
    }

    private fun lookupKommuneForPoint(
        pointId: Long,
        matrikkelContext: MatrikkelContext,
        kommuneMap: Map<Int, Kommune>
    ): String? {
        return try {
            val matrikkelenheterIds = matrikkelenhetService.findMatrikkelenheterForTeiggrensepunkt(
                TeiggrensepunktId(pointId),
                matrikkelContext
            )
            getKommuneNumberFromMatrikkelenhet(matrikkelenheterIds, matrikkelContext, kommuneMap)
        } catch (e: Exception) {
            log.warn("Failed to lookup kommune for point {}: {}", pointId, e.message)
            null
        }
    }

    private fun lookupKommuneForLine(
        lineId: Long,
        matrikkelContext: MatrikkelContext,
        kommuneMap: Map<Int, Kommune>
    ): String? {
        return try {
            val matrikkelenheterIds = matrikkelenhetService.findMatrikkelenheterForTeiggrense(
                TeiggrenseId(lineId),
                matrikkelContext
            )
            getKommuneNumberFromMatrikkelenhet(matrikkelenheterIds, matrikkelContext, kommuneMap)

        } catch (e: Exception) {
            log.warn("Failed to lookup kommune for line {}: {}", lineId, e.message)
            null
        }
    }

    private fun getKommuneNumberFromMatrikkelenhet(matrikkelenheterIds: MatrikkelenhetIdList?,
                                                   matrikkelContext: MatrikkelContext,
                                                   kommuneMap: Map<Int, Kommune>): String? {

        val kommuneNumbers = mutableSetOf<String>()
        matrikkelenheterIds?.item?.forEach { matrikkelenhetId ->
            try {

                val matrikkelenhetObject = storeService.getObject(matrikkelenhetId, matrikkelContext)
                if (matrikkelenhetObject is Matrikkelenhet) {
                    val kommuneId = matrikkelenhetObject.matrikkelnummer?.kommuneId
                    kommuneId?.let {
                        val kommune = kommuneMap[it.value.toInt()]
                        kommune?.kommunenummer?.let { number -> kommuneNumbers.add(number) }
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to process matrikkelenhet {}: {}", matrikkelenhetId, e.message ?: "")
            }
        }
        return if (kommuneNumbers.isNotEmpty()) kommuneNumbers.sorted().joinToString(",") else null

    }

}
