package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import org.slf4j.LoggerFactory
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EndringsnummerService(
    private val jdbcTemplate: JdbcTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(EndringsnummerService::class.java)
    }


    fun getLastProcessedEndringsnummer(): Long? {

        return try {
            jdbcTemplate.queryForObject(
                """
            SELECT endringsnummer
            FROM nibas_arbeidsliste_schema.matrikkel_endringsnummer
            ORDER BY oppdateringsdato DESC
            LIMIT 1
            """, Long::class.java
            )
        } catch (e: EmptyResultDataAccessException) {
            log.error("No endringsnummer found in database table matrikkel_endringsnummer. Error: ${e.message}")
            null
        }
    }

    @Transactional
    fun saveProcessedEndringsnummer(endringsnummer: Long) {
        jdbcTemplate.update(
            """
            INSERT INTO nibas_arbeidsliste_schema.matrikkel_endringsnummer (endringsnummer, oppdateringsdato)
            VALUES (?, CURRENT_TIMESTAMP)
            ON CONFLICT (endringsnummer) DO NOTHING
            """,
            endringsnummer
        )
    }

}
