package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import no.kartverket.nibas.nibasarbeidsliste.service.KommuneLookupService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * Service for converting raw M22 data (grenselinje + grensepunkt) into final NIBAS format
 */
@Service
@Order(1)
class RawDataConverterService(
    private val jdbcTemplate: JdbcTemplate,
    private val kommuneLookupService: KommuneLookupService
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(RawDataConverterService::class.java)

    val runOnStartup = true
    override fun run(args: ApplicationArguments?) {
        if (runOnStartup) {
            logger.info("=== RawDataConverterService ===")
            kommuneLookupService.refreshKommuneLookupTable()
            val existingCount = countConvertedGrenselinjer()
            if (existingCount > 0) {
                logger.info("Data already exists in matrikkel_grenselinje table ({} records)", existingCount)
            } else {
                Thread.sleep(60000)
                convertRawDataToMergedTable()
            }
            logger.info("=== Test completed ===")
        }
    }

    fun convertRawDataToMergedTable(): Int {
        val rawCount = countRawGrenselinjer()
        logger.info("Converting {} raw grenselinjer to merged table...", rawCount)
        val convertedCount = convertAllGrenselinjer()
        logger.info("Converted {} grenselinje records", convertedCount)

        return convertedCount
    }


    private fun convertAllGrenselinjer(): Int {
        // Convert all grenselinjer to LINESTRING geometry
        val sql = """
            INSERT INTO nibas_arbeidsliste_schema.matrikkel_grenselinje (
                id, hjelpelinjetype_id, omtvistet, folgerterrengdetalj_id,
                administrativgrensekode_id, malemetode_id, noyaktighet, datafangstdato,
                lagretnoyaktighetsklasse, geom, oppdateringsdato, kommunenr1, kommunenr2,
                kommunenavn1, kommunenavn2, informasjoncache, versjon, versjon_id, oppdatert_av
            )
            SELECT
                gl.id,
                gl.hjelpelinjetypeid,
                gl.omtvistet,
                gl.folgerterrengdetaljid,
                gl.administrativgrensekodeid,
                -- Use grensepunkt malemetode if grenselinje is NULL. 0 is valid TERRENGMALT.
                CASE
                    WHEN gl.malemetodeid IS NULL THEN
                        CASE
                            WHEN gp_start.malemetodeid IS NOT NULL AND gp_end.malemetodeid IS NOT NULL THEN
                                CASE WHEN gp_start.noyaktighet >= gp_end.noyaktighet
                                     THEN gp_start.malemetodeid
                                     ELSE gp_end.malemetodeid END
                            WHEN gp_start.malemetodeid IS NOT NULL THEN gp_start.malemetodeid
                            WHEN gp_end.malemetodeid IS NOT NULL THEN gp_end.malemetodeid
                            ELSE gl.malemetodeid
                        END
                    ELSE gl.malemetodeid
                END as malemetode_id,
                -- Use grensepunkt noyaktighet if grenselinje is NULL. Use higher value = worse accuracy.
                CASE
                    WHEN gl.noyaktighet IS NULL THEN
                        CASE
                            WHEN gp_start.noyaktighet > 0 AND gp_end.noyaktighet > 0 THEN
                                GREATEST(gp_start.noyaktighet, gp_end.noyaktighet)
                            WHEN gp_start.noyaktighet > 0 THEN gp_start.noyaktighet
                            WHEN gp_end.noyaktighet > 0 THEN gp_end.noyaktighet
                            ELSE gl.noyaktighet
                        END
                    ELSE gl.noyaktighet
                END as noyaktighet,
                gl.datafangstdato,
                gl.lagretnoyaktighetsklasse,
                CASE
                    -- If the start or end point is missing, we cannot create a geometry
                    WHEN gp_start.id IS NULL OR gp_end.id IS NULL THEN NULL
                    -- Polyline with intermediate points: start → intermediate points → end
                    WHEN gl.kurvesegmenttype = 'polyline' AND gl.kurvepositions IS NOT NULL THEN
                        ST_MakeLine(ARRAY[
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gp_start.positionx, gp_start.positiony, gp_start.koordinatsystemkodeid)
                        ] || ARRAY(
                            SELECT ST_MakePoint(
                                ST_X(nibas_arbeidsliste_schema.transform_point_to_utm33((c->>0)::double precision, (c->>1)::double precision, gl.kurvekoordinatsystemkode)),
                                ST_Y(nibas_arbeidsliste_schema.transform_point_to_utm33((c->>0)::double precision, (c->>1)::double precision, gl.kurvekoordinatsystemkode))
                            )
                            FROM JSON_ARRAY_ELEMENTS(gl.kurvepositions::json) c
                        ) || ARRAY[
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gp_end.positionx, gp_end.positiony, gp_end.koordinatsystemkodeid)
                        ])
                    -- If it's an arc with an arc point, create a 3-point line
                    WHEN gl.kurvesegmenttype = 'arc' AND gl.kurvebuepunktx IS NOT NULL AND gl.kurvebuepunkty IS NOT NULL THEN
                        ST_MakeLine(ARRAY[
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gp_start.positionx, gp_start.positiony, gp_start.koordinatsystemkodeid),
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gl.kurvebuepunktx, gl.kurvebuepunkty, gl.kurvekoordinatsystemkode),
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gp_end.positionx, gp_end.positiony, gp_end.koordinatsystemkodeid)
                        ])
                    -- Grenselinje only got two points, start and end point from grensepunkt.
                    ELSE
                        ST_MakeLine(
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gp_start.positionx, gp_start.positiony, gp_start.koordinatsystemkodeid),
                            nibas_arbeidsliste_schema.transform_point_to_utm33(gp_end.positionx, gp_end.positiony, gp_end.koordinatsystemkodeid)
                        )
                END as geom,
                gl.oppdateringsdato,
                -- Fix missing kommune using grensepunkt data when grenselinje has incomplete kommune info
                CASE
                    -- If grenselinje has 2 kommuner, use the first one
                    WHEN gl.kommunenrstrengcache LIKE '%,%' THEN
                        SPLIT_PART(gl.kommunenrstrengcache, ',', 1)
                    -- If grenselinje has only 1 kommune, keep it as first kommune
                    ELSE
                        gl.kommunenrstrengcache
                END as kommunenr1,
                CASE
                    -- If grenselinje has 2 kommuner, use second one
                    WHEN gl.kommunenrstrengcache LIKE '%,%' THEN
                        SPLIT_PART(gl.kommunenrstrengcache, ',', 2)
                    -- If grenselinje has only 1 kommune AND it's Kommune/fylkesgrense (code 1 or 2), find the missing one from grensepunkt
                    WHEN gl.administrativgrensekodeid IN (1, 2) THEN
                        COALESCE(
                            -- Try to find different kommune from start point
                            CASE WHEN gp_start.kommunenrstrengcache LIKE '%,%' THEN
                                CASE WHEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 1) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 1)
                                     WHEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 2) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 2)
                                     ELSE NULL END
                            END,
                            -- Try to find different kommune from end point
                            CASE WHEN gp_end.kommunenrstrengcache LIKE '%,%' THEN
                                CASE WHEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 1) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 1)
                                     WHEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 2) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 2)
                                     ELSE NULL END
                            END
                        )
                END as kommunenr2,
                kl1.kommunenavn as kommunenavn1,
                kl2.kommunenavn as kommunenavn2,
                gl.informasjon,
                gl.versjon,
                gl.versjonid,
                gl.oppdatertav
            FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje gl
            LEFT JOIN nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt gp_start
                ON gl.kurvestartpunktid = gp_start.id
            LEFT JOIN nibas_arbeidsliste_schema.raw_matrikkel_grensepunkt gp_end
                ON gl.kurveendpunktid = gp_end.id
            LEFT JOIN nibas_arbeidsliste_schema.kommune_lookup kl1
                ON (CASE 
                        WHEN gl.kommunenrstrengcache LIKE '%,%' THEN SPLIT_PART(gl.kommunenrstrengcache, ',', 1)
                        ELSE gl.kommunenrstrengcache
                    END) = kl1.kommunenr
            LEFT JOIN nibas_arbeidsliste_schema.kommune_lookup kl2
                ON (CASE 
                        WHEN gl.kommunenrstrengcache LIKE '%,%' THEN SPLIT_PART(gl.kommunenrstrengcache, ',', 2)
                        WHEN gl.administrativgrensekodeid IN (1, 2) THEN COALESCE(
                            CASE WHEN gp_start.kommunenrstrengcache LIKE '%,%' THEN
                                CASE WHEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 1) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 1)
                                     WHEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 2) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_start.kommunenrstrengcache, ',', 2)
                                     ELSE NULL END
                            END,
                            CASE WHEN gp_end.kommunenrstrengcache LIKE '%,%' THEN
                                CASE WHEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 1) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 1)
                                     WHEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 2) != gl.kommunenrstrengcache
                                     THEN SPLIT_PART(gp_end.kommunenrstrengcache, ',', 2)
                                     ELSE NULL END
                            END
                        )
                    END) = kl2.kommunenr
        """.trimIndent()

        return jdbcTemplate.update(sql)
    }

    private fun countRawGrenselinjer(): Int {
        val sql = "SELECT COUNT(*) FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje"
        return jdbcTemplate.queryForObject(sql, Int::class.java) ?: 0
    }

    private fun countConvertedGrenselinjer(): Int {
        val sql = "SELECT COUNT(*) FROM nibas_arbeidsliste_schema.matrikkel_grenselinje"
        return jdbcTemplate.queryForObject(sql, Int::class.java) ?: 0
    }

    fun rebuildAfterSync(): Int {
        logger.info("Rebuilding matrikkel_grenselinje from raw data after sync...")

        val deletedCount = jdbcTemplate.update("DELETE FROM nibas_arbeidsliste_schema.matrikkel_grenselinje")
        logger.info("Deleted {} existing matrikkel_grenselinje records", deletedCount)

        val convertedCount = convertAllGrenselinjer()
        logger.info("Rebuilt {} matrikkel_grenselinje records from raw data", convertedCount)

        return convertedCount
    }

}
