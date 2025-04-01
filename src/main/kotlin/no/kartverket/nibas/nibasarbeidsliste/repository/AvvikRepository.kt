package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AvvikRepository : JpaRepository<Avvik, Long> {

    @Query("SELECT a FROM Avvik a JOIN a.kommuner k WHERE k.kommuneLokalID= :lokalid")
    fun findKommuneByLokalId(@Param("lokalid") lokalid: String): List<Avvik>

    @Query("SELECT a FROM Avvik a JOIN a.kommuner k WHERE k.kommuneLokalID= :lokalid AND a.grensetype IN :grensetyper")
    fun findKommuneByLokalIdAndGrensetyper(@Param("lokalid") lokalid: String, @Param("grensetyper") grensetyper: List<String>): List<Avvik>

    @Query("SELECT a FROM Avvik a WHERE a.grensetype IN :grensetyper")
    fun findAllByGrensetyper(grensetyper: List<String>?): List<Avvik>

    @Query("SELECT a FROM Avvik a WHERE a.grensetype IN :grensetyper")
    fun findAllByGrensetyper(grensetyper: List<String>?, pageable: Pageable): Page<Avvik>
}
