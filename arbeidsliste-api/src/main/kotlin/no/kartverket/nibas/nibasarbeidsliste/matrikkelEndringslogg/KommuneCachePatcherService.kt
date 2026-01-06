package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

/**
 * Service to patch missing kommune cache in raw_matrikkel_grenselinje.
 * Administrative boundaries (code 1 and 2) should have two kommuner in kommunenrstrengcache.
 * This service runs before data conversion to ensure complete kommune information.
 */
@Service
@Order(0) // Run before RawDataConverterService
class KommuneCachePatcherService(
    @param:Value($$"${matrikkelen.patch-on-startup:false}") private val patchOnStartup: Boolean,
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (patchOnStartup) {
            log.info("=== KommuneCachePatcherService: Starting patch process ===")
            val fixesApplied = applyHardcodedFixes()
            log.info("=== KommuneCachePatcherService: Patching completed. {} fixes applied. ===", fixesApplied)
        }
    }

    /**
     * Applies hardcoded fixes for known problematic grenselinje IDs.
     *
     * @return The number of database rows that were actually updated.
     */
    private fun applyHardcodedFixes(): Int {
        log.info("Applying hardcoded fixes for known problematic grenselinje IDs...")

        // Hardcoded since they only had one Matrikkelenhet each in an older state.
        val problematicIds = listOf(119161248L, 119161251L, 119161262L, 119161265L, 119161267L, 119175512L, 119175515L)
        val kommuneNrStreng = "4036,4222"
        var totalFixes = 0

        log.info("Attempting to apply fix to {} potential grenselinje IDs.", problematicIds.size)

        problematicIds.forEach { id ->
            try {
                val rowsAffected = conditionallyUpdateKommuneCache(id, kommuneNrStreng)
                if (rowsAffected > 0) {
                    log.info("SUCCESS: Applied fix to grenselinje id={}", id)
                    totalFixes++
                } else {
                    log.debug("SKIPPED: Grenselinje id={} either did not exist or already had a valid kommune cache.", id)
                }
            } catch (e: Exception) {
                log.error("FAILURE: Failed to apply fix for grenselinje id={}", id, e)
            }
        }
        return totalFixes
    }

    /**
     * Conditionally updates the kommune cache for a given line ID.
     * The UPDATE only occurs if the row exists and its `kommunenrstrengcache` is
     * either NULL or does not have the expected length (9 for "xxxx,xxxx").
     *
     * @return The number of rows affected.
     */
    private fun conditionallyUpdateKommuneCache(lineId: Long, kommuneCache: String): Int {
        val sql = """
            UPDATE nibas_arbeidsliste_schema.raw_matrikkel_grenselinje
            SET kommunenrstrengcache = ?
            WHERE id = ?
              AND (kommunenrstrengcache IS NULL OR LENGTH(kommunenrstrengcache) != 9)
        """.trimIndent()

        return jdbcTemplate.update(sql, kommuneCache, lineId)
    }

}
