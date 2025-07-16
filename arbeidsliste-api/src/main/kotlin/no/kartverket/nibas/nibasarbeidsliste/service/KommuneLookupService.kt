package no.kartverket.nibas.nibasarbeidsliste.service

import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate

/**
 * Service for populating and maintaining the kommune_lookup table
 * Fetches current kommune data from Kartverket API and updates local lookup table
 */
@Service
class KommuneLookupService(
    private val jdbcTemplate: JdbcTemplate,
    private val restTemplate: RestTemplate
) {

    companion object {
        private val log = LoggerFactory.getLogger(KommuneLookupService::class.java)
    }

    private val kommuneApiUrl = "https://api.kartverket.no/kommuneinfo/v1/kommuner"

    @Transactional
    fun refreshKommuneLookupTable(): Int {
        log.info("Refreshing kommune lookup table from Kartverket API...")

        val existingCount = countKommunerInLookup()
        log.info("Current lookup table has {} kommuner.", existingCount)

        val kommuner = try {
            restTemplate.getForObject(kommuneApiUrl, Array<KommuneDto>::class.java)
                ?: throw IllegalStateException("API returned null body for kommuner")
        } catch (e: Exception) {
            log.error("Failed to fetch kommuner from API: {}", e.message, e)
            throw IllegalStateException("Failed to fetch kommuner from Kartverket API at $kommuneApiUrl", e)
        }

        log.info("Fetched {} kommuner from API.", kommuner.size)

        clearLookupTable()
        populateLookupTable(kommuner.toList())

        val newCount = countKommunerInLookup()
        log.info("Kommune lookup table updated successfully. New count: {} kommuner.", newCount)

        return newCount
    }

    private fun clearLookupTable() {
        val sql = "DELETE FROM nibas_arbeidsliste_schema.kommune_lookup"
        jdbcTemplate.update(sql)
    }

    private fun populateLookupTable(kommuner: List<KommuneDto>) {
        val sql = """
            INSERT INTO nibas_arbeidsliste_schema.kommune_lookup (kommunenr, kommunenavn)
            VALUES (?, ?)
        """.trimIndent()

        val batchArgs = kommuner.map { kommune ->
            arrayOf(kommune.kommunenummer, kommune.kommunenavnNorsk)
        }

        jdbcTemplate.batchUpdate(sql, batchArgs)
    }

    private fun countKommunerInLookup(): Int {
        val sql = "SELECT COUNT(*) FROM nibas_arbeidsliste_schema.kommune_lookup"
        return jdbcTemplate.queryForObject(sql, Int::class.java) ?: 0
    }
}

data class KommuneDto(
    @param:JsonProperty("kommunenummer")
    val kommunenummer: String,

    @param:JsonProperty("kommunenavnNorsk")
    val kommunenavnNorsk: String
)
