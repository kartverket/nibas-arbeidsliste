package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StatisticsRepository : JpaRepository<Avvik, Long> {

    @Query("SELECT COUNT(a) FROM Avvik a")
    fun countTotalAvvik(): Long

    @Query("SELECT a.status, COUNT(a) FROM Avvik a GROUP BY a.status")
    fun countByStatus(): List<Array<Any>>

    @Query("SELECT COUNT(DISTINCT k.kommuneLokalID) FROM Avvik a JOIN a.kommuner k")
    fun countDistinctKommunerWithAvvik(): Long

    @Query("SELECT a.grensetype, COUNT(DISTINCT a.id) FROM Avvik a GROUP BY a.grensetype")
    fun countBordersByGrensetype(): List<Array<Any>>

    @Query("SELECT a.grensetype, COUNT(coord) FROM Avvik a JOIN a.koordinaterMedAvvik coord GROUP BY a.grensetype")
    fun countAvvikPointsByGrensetype(): List<Array<Any>>

    @Query("SELECT a.status, COUNT(a) FROM Avvik a WHERE a.grensetype IN ('Kommunegrense', 'Fylkesgrense') GROUP BY a.status")
    fun countByStatusForArbeidsGrensetype(): List<Array<Any>>
}
