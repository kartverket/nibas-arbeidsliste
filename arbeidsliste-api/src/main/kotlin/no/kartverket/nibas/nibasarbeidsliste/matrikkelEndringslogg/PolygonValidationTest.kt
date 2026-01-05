package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * Test to check if boundary lines form closed polygons for all kommuner.
 */
@Service
@Order(2)
class PolygonValidationTest(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(PolygonValidationTest::class.java)

    override fun run(args: ApplicationArguments) {

        logger.info("=== Starting Polygon Validation for All Kommuner ===")
        testAllKommuner()
        logger.info("=== Polygon Validation Test Completed ===")
    }

    fun testAllKommuner() {
        val kommuner = getAllKommuner()
        logger.info("Found {} kommuner to test", kommuner.size)

        val validationResults = kommuner.map { it to testKommunePolygonClosure(it.kommunenr) }

        logOverallSummary(validationResults)
        logGeometryTypeSummary(validationResults)

        logDanglingLinesAnalysis(kommuner)
        logMissingKommuneDataAnalysis()
    }

    /**
     * Logs the main summary report, showing perfect vs. problematic boundaries.
     */
    private fun logOverallSummary(results: List<Pair<KommuneInfo, ValidationResult>>) {
        val problemList = mutableListOf<String>()
        val perfectKommuner = results.count { it.second.isPerfect }
        val problemKommuner = results.size - perfectKommuner

        results.filterNot { it.second.isPerfect }.forEach { (kommune, result) ->
            problemList.add("${kommune.kommunenr} (${kommune.kommunenavn}): ${result.problemDescription}")
        }

        logger.info("=================================================================================")
        logger.info("FINAL SUMMARY:")
        logger.info("Total kommuner tested: {}", results.size)
        logger.info("Perfect boundaries: {}", perfectKommuner)
        logger.info("Problem boundaries: {}", problemKommuner)

        if (problemList.isNotEmpty()) {
            logger.info("Kommuner with boundary problems:")
            problemList.forEach { logger.info("  - {}", it) }
        }

        if (results.isNotEmpty()) {
            val successRate = (perfectKommuner.toDouble() / results.size * 100)
            val formattedSuccessRate = String.format("%.1f", successRate)

            if (perfectKommuner == results.size) {
                logger.info("Polygon validation success rate={}%", formattedSuccessRate)
            } else {
                logger.error("Polygon Validation feilet: {}/{} kommuner ok ({}%). Feilet: {}. Ser mer detaljerte logger i grafana",
                    perfectKommuner, results.size, formattedSuccessRate, problemList.joinToString(", "))
            }
        }
    }

    /**
     * Logs a summary of the geometry types for all perfect boundaries.
     */
    private fun logGeometryTypeSummary(results: List<Pair<KommuneInfo, ValidationResult>>) {
        logger.info("=== Geometry Type Summary ===")
        val perfectResults = results.filter { it.second.isPerfect }

        val singlePolygonCount = perfectResults.count { it.second.problemDescription.contains("single polygon") }
        val multiPolygonCount = perfectResults.count { it.second.problemDescription.contains("MultiPolygon") }

        logger.info("Single polygon kommuner: {}", singlePolygonCount)
        logger.info("MultiPolygon kommuner (with islands): {}", multiPolygonCount)
    }

    private fun logDanglingLinesAnalysis(allKommuner: List<KommuneInfo>) {
        logger.info("=== Missing Grenselinjer Analysis ===")
        val missingConnections = findAllMissingGrenselinjer()
        if (missingConnections.isEmpty()) {
            logger.info("No missing grenselinjer detected - all boundaries have proper connections")
        } else {
            logger.info("Found {} kommuner with missing grenselinjer:", missingConnections.size)
            missingConnections.forEach { (kommuneNr, danglingPoints) ->
                val kommuneInfo = allKommuner.find { it.kommunenr == kommuneNr }
                val kommuneName = kommuneInfo?.kommunenavn ?: "Unknown"
                logger.info("  - {} ({}): {} dangling points", kommuneNr, kommuneName, danglingPoints.size)
                logger.info("    Dangling points: {}{}", danglingPoints.take(5).joinToString(", "), if (danglingPoints.size > 5) "..." else "")
            }
        }
        logger.info("=== Missing Grenselinjer Analysis Completed ===")
    }

    private fun logMissingKommuneDataAnalysis() {
        logger.info("=== Missing Kommune Analysis ===")
        printMissingKommuneLines()
        logger.info("=== Missing Kommune Analysis Completed ===")
    }

    private fun getAllKommuner(): List<KommuneInfo> {
        val sql = """
            SELECT kommunenr, kommunenavn
            FROM nibas_arbeidsliste_schema.kommune_lookup
            ORDER BY kommunenr
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            KommuneInfo(rs.getString("kommunenr"), rs.getString("kommunenavn"))
        }
    }

    /**
     * Test if boundary lines for a kommune form proper closed polygons.
     * Returns validation result with problem description.
     */
    fun testKommunePolygonClosure(kommunenr: String): ValidationResult {

        val lineCount = countBoundaryLines(kommunenr)

        if (lineCount == 0) {
            return ValidationResult(false, "No boundary lines found")
        }

        val nullGeomResult = checkNullGeometries(kommunenr)
        if (nullGeomResult.nullCount > 0) {
            return ValidationResult(false, "Has ${nullGeomResult.nullCount} NULL geometries causing gaps")
        }

        val polygonResult = testPolygonConstruction(kommunenr)
        if (polygonResult.polygonCount > 0) {
            // Perfect - can create actual polygons
            val description = when {
                polygonResult.polygonCount == 1 -> "Perfect single polygon boundary"
                else -> "Perfect MultiPolygon with ${polygonResult.polygonCount} parts (islands/exclaves)"
            }
            return ValidationResult(true, description)
        }

        val closureResult = testBoundaryClosure(kommunenr)
        if (closureResult.isClosed) {
            return ValidationResult(true, "Good closed boundary (polygon creation failed but forms closed loop)")
        }

        return ValidationResult(false, "Cannot create closed boundary from lines")
    }

    private fun countBoundaryLines(kommunenr: String): Int {
        val sql = """
            SELECT COUNT(*)
            FROM nibas_arbeidsliste_schema.matrikkel_grenselinje
            WHERE kommunenr1 = ? OR kommunenr2 = ?
        """.trimIndent()

        return jdbcTemplate.queryForObject(sql, Int::class.java, kommunenr, kommunenr) ?: 0
    }

    private fun testBoundaryClosure(kommunenr: String): ClosureResult {
        val sql = """
            SELECT
                COUNT(*) as total_lines,
                ST_IsClosed(ST_LineMerge(ST_Collect(geom))) as is_closed,
                ST_IsValid(ST_LineMerge(ST_Collect(geom))) as is_valid,
                ST_GeometryType(ST_LineMerge(ST_Collect(geom))) as merged_type,
                ST_Length(ST_LineMerge(ST_Collect(geom))) as total_length_meters
            FROM nibas_arbeidsliste_schema.matrikkel_grenselinje
            WHERE kommunenr1 = ? OR kommunenr2 = ?
        """.trimIndent()

        val result = jdbcTemplate.queryForMap(sql, kommunenr, kommunenr)

        return ClosureResult(
            isClosed = result["is_closed"] as Boolean,
            isValid = result["is_valid"] as Boolean,
            totalLines = (result["total_lines"] as Number).toInt()
        )
    }

    private fun testPolygonConstruction(kommunenr: String): PolygonResult {
        val sql = """
            WITH boundary_collection AS (
                SELECT ST_Collect(geom) as all_boundaries
                FROM nibas_arbeidsliste_schema.matrikkel_grenselinje
                WHERE kommunenr1 = ? OR kommunenr2 = ?
            ),
            polygon_attempt AS (
                SELECT
                    ST_Polygonize(all_boundaries) as polygons,
                    ST_IsValid(ST_Polygonize(all_boundaries)) as polygons_valid,
                    ST_NumGeometries(ST_Polygonize(all_boundaries)) as polygon_count
                FROM boundary_collection
            ),
            polygon_analysis AS (
                SELECT
                    polygon_count,
                    polygons_valid,
                    CASE
                        WHEN polygon_count > 0 THEN ST_Area(polygons)
                        ELSE 0
                    END as total_area_sqm,
                    CASE
                        WHEN polygon_count > 1 THEN
                            ST_GeometryType(ST_CollectionExtract(polygons, 3))
                        WHEN polygon_count = 1 THEN
                            ST_GeometryType(polygons)
                        ELSE 'NONE'
                    END as geometry_type
                FROM polygon_attempt
            )
            SELECT
                polygon_count,
                polygons_valid,
                total_area_sqm,
                geometry_type
            FROM polygon_analysis
        """.trimIndent()

        val result = jdbcTemplate.queryForMap(sql, kommunenr, kommunenr)

        return PolygonResult(
            polygonCount = (result["polygon_count"] as Number).toInt(),
            isValid = result["polygons_valid"] as? Boolean ?: false,
            totalArea = (result["total_area_sqm"] as Number).toDouble(),
            geometryType = result["geometry_type"] as String
        )
    }

    private fun checkNullGeometries(kommunenr: String): NullGeomResult {
        val sql = """
            SELECT
                COUNT(*) as total_lines,
                COUNT(CASE WHEN geom IS NULL THEN 1 END) as null_count
            FROM nibas_arbeidsliste_schema.matrikkel_grenselinje
            WHERE kommunenr1 = ? OR kommunenr2 = ?
        """.trimIndent()

        val result = jdbcTemplate.queryForMap(sql, kommunenr, kommunenr)

        return NullGeomResult(
            totalLines = (result["total_lines"] as Number).toInt(),
            nullCount = (result["null_count"] as Number).toInt()
        )
    }

    fun findAllMissingGrenselinjer(): Map<String, List<String>> {
        val sql = """
            WITH point_connections AS (
                SELECT
                    punkt_id,
                    COUNT(*) as connection_count,
                    STRING_AGG(DISTINCT kommuner, '; ') as affected_kommuner
                FROM (
                    SELECT
                        kurvestartpunktid as punkt_id,
                        kommunenrstrengcache as kommuner
                    FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje
                    WHERE kurvestartpunktid IS NOT NULL
                    UNION ALL
                    SELECT
                        kurveendpunktid as punkt_id,
                        kommunenrstrengcache as kommuner
                    FROM nibas_arbeidsliste_schema.raw_matrikkel_grenselinje
                    WHERE kurveendpunktid IS NOT NULL
                ) all_connections
                GROUP BY punkt_id
            ),
            dangling_points AS (
                SELECT
                    punkt_id,
                    affected_kommuner
                FROM point_connections
                WHERE connection_count = 1
                AND affected_kommuner IS NOT NULL
            )
            SELECT
                kommune,
                COUNT(*) as dangling_count,
                STRING_AGG(punkt_id::text, ', ') as dangling_points
            FROM dangling_points
            CROSS JOIN LATERAL unnest(string_to_array(affected_kommuner, '; ')) as t(kommune)
            WHERE kommune ~ '^\d{4}$'
            GROUP BY kommune
            HAVING COUNT(*) > 1
            ORDER BY kommune
        """.trimIndent()

        val results = mutableMapOf<String, List<String>>()

        jdbcTemplate.query(sql) { rs ->
            val kommune = rs.getString("kommune")
            val danglingPoints = rs.getString("dangling_points").split(", ")
            results[kommune] = danglingPoints
        }

        return results
    }

    /**
     * Print grenselinjer with admin code 1 or 2 that are missing kommune2
     */
    private fun printMissingKommuneLines() {
        val sql = """
            SELECT
                id, administrativgrensekode_id, kommunenr1, kommunenr2
            FROM nibas_arbeidsliste_schema.matrikkel_grenselinje
            WHERE administrativgrensekode_id IN (1, 2)
            AND kommunenr2 IS NULL
            ORDER BY administrativgrensekode_id, id
        """.trimIndent()

        val results = jdbcTemplate.queryForList(sql)

        if (results.isEmpty()) {
            logger.info("No administrative boundaries (code 1 or 2) are missing kommune2 - all fixed!")
        } else {
            logger.info("Found {} administrative boundaries missing kommune2:", results.size)

            val groupedByType = results.groupBy { it["administrativgrensekode_id"] }

            groupedByType.forEach { (adminCode, lines) ->
                val typeName = when (adminCode) {
                    1 -> "Kommune borders"
                    2 -> "Fylke borders"
                    else -> "Admin code $adminCode"
                }
                logger.info("  {} ({} lines):", typeName, lines.size)
                lines.forEach { line ->
                    logger.info("    ID: {}, kommune1: {}", line["id"], line["kommunenr1"])
                }
            }
        }
    }

}

data class KommuneInfo(val kommunenr: String, val kommunenavn: String)

data class ValidationResult(val isPerfect: Boolean, val problemDescription: String)

data class ClosureResult(val isClosed: Boolean, val isValid: Boolean, val totalLines: Int)

data class PolygonResult(val polygonCount: Int, val isValid: Boolean, val totalArea: Double, val geometryType: String)

data class NullGeomResult(val totalLines: Int, val nullCount: Int)
