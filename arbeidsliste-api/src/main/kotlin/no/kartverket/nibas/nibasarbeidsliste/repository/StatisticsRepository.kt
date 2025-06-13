package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface StatisticsRepository : JpaRepository<Avvik, Long> {

    @Query("SELECT COUNT(a) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false)")
    fun countTotalAvvik(): Long

    @Query("SELECT a.status, COUNT(a) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false) GROUP BY a.status")
    fun countByStatus(): List<Array<Any>>

    @Query("SELECT a.grensetype, COUNT(a) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false) GROUP BY a.grensetype")
    fun countByGrensetype(): List<Array<Any>>

    @Query("SELECT COUNT(DISTINCT k.kommuneLokalID) FROM Avvik a JOIN a.kommuner k JOIN a.koordinaterMedAvvik coord WHERE (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false)")
    fun countDistinctKommunerWithAvvik(): Long

    @Query("SELECT a.grensetype, COUNT(DISTINCT a.id) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false) GROUP BY a.grensetype")
    fun countBordersByGrensetype(): List<Array<Any>>

    @Query("SELECT a.grensetype, COUNT(coord) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false) GROUP BY a.grensetype")
    fun countRealAvvikPointsByGrensetype(): List<Array<Any>>

    @Query("SELECT a.grensetype, COUNT(coord) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE coord.erPaaMatrikkelLinje = true GROUP BY a.grensetype")
    fun countHelperPointsByGrensetype(): List<Array<Any>>

    @Query("SELECT a.status, COUNT(a) FROM Avvik a JOIN a.koordinaterMedAvvik coord WHERE a.grensetype IN ('Kommunegrense', 'Fylkesgrense') AND (coord.erPaaMatrikkelLinje IS NULL OR coord.erPaaMatrikkelLinje = false) GROUP BY a.status")
    fun countByStatusForArbeidsGrensetype(): List<Array<Any>>
}