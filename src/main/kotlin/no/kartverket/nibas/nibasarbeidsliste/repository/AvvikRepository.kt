package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AvvikRepository : JpaRepository<Avvik, Long> {

    @Query("SELECT a FROM Avvik a JOIN a.kommuner k WHERE k.kommuneLokalID= :lokalid")
    fun findKommuneByLokalId(@Param("lokalid") lokalid: String): List<Avvik>
}
