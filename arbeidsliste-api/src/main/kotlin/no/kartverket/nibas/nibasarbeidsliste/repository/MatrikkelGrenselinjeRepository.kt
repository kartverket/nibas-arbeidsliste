package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.MatrikkelGrenselinje
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Repository for accessing MatrikkelGrenselinje entities from the database.
 */
@Repository
interface MatrikkelGrenselinjeRepository : JpaRepository<MatrikkelGrenselinje, Long> {

    /**
     * Find all boundary lines for a specific municipality.
     * @param kommunenummer Exact municipality number to filter by
     * @return List of MatrikkelGrenselinje entities matching the given municipality number
     */
    @Query("""
        SELECT DISTINCT m
        FROM MatrikkelGrenselinje m
        WHERE m.kommunenr1 = :kommunenummer
           OR m.kommunenr2 = :kommunenummer
    """)
    @Transactional(readOnly = true)
    fun findByKommune(@Param("kommunenummer") kommunenummer: String): List<MatrikkelGrenselinje>

    /**
     * Find all unique municipality numbers that have boundary lines in the database.
     * @return List of unique municipality numbers, sorted numerically
     */
    @Query("""
        SELECT DISTINCT m.kommunenr1 FROM MatrikkelGrenselinje m WHERE m.kommunenr1 IS NOT NULL
        UNION
        SELECT DISTINCT m.kommunenr2 FROM MatrikkelGrenselinje m WHERE m.kommunenr2 IS NOT NULL
        ORDER BY 1
    """)
    @Transactional(readOnly = true)
    fun findDistinctKommuner(): List<String>
}
