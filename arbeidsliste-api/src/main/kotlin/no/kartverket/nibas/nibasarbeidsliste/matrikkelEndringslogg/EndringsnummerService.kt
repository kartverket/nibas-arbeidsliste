package no.kartverket.nibas.nibasarbeidsliste.matrikkelEndringslogg

import org.slf4j.LoggerFactory
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

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

    /**
     * Prevents multiple pods from syncing simultaneously.
     * Uses atomic database operation
     *
     * @return true if lock acquired, false if another sync already running
     */
    @Transactional
    fun tryAcquireSyncLock(): Boolean {
        val lockTimeoutSeconds: Long = 3 * 60 * 60
        val lockUntil = Instant.now().plusSeconds(lockTimeoutSeconds)

        val updated = jdbcTemplate.update(
            """
            UPDATE nibas_arbeidsliste_schema.sync_lock
            SET locked_until = ?
            WHERE id = 1 AND (locked_until IS NULL OR locked_until < CURRENT_TIMESTAMP)
            """,
            Timestamp.from(lockUntil)
        )

        return if (updated > 0) {
            log.info("Sync lock acquired")
            true
        } else {
            log.info("Sync already running, skipping")
            false
        }
    }

    /**
     * Cleans up sync lock. Always called in finally block.
     * Safe to call even if lock wasn't acquired.
     */
    @Transactional
    fun releaseSyncLock() {
        jdbcTemplate.update(
            """
            UPDATE nibas_arbeidsliste_schema.sync_lock 
            SET locked_until = NULL
            WHERE id = 1
            """)
        log.info("Sync lock released")
    }

}
