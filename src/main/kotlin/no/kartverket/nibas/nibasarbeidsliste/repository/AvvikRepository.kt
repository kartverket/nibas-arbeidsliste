package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
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

    @Query("SELECT a FROM Avvik a JOIN a.kommuner k WHERE k.kommuneLokalID= :lokalid AND a.status IN :statuses")
    fun findKommuneByLokalIdAndStatusIn(@Param("lokalid") lokalid: String, @Param("statuses") statuses: List<AvvikStatus>): List<Avvik>

    @Query("SELECT a FROM Avvik a JOIN a.kommuner k WHERE k.kommuneLokalID= :lokalid AND a.grensetype IN :grensetyper")
    fun findKommuneByLokalIdAndGrensetyper(@Param("lokalid") lokalid: String, @Param("grensetyper") grensetyper: List<String>): List<Avvik>

    @Query("SELECT a FROM Avvik a WHERE a.grensetype IN :grensetyper")
    fun findAllByGrensetyper(grensetyper: List<String>?): List<Avvik>

    @Query("SELECT a FROM Avvik a WHERE a.grensetype IN :grensetyper")
    fun findAllByGrensetyper(grensetyper: List<String>?, pageable: Pageable): Page<Avvik>

    @Query("SELECT a FROM Avvik a WHERE a.id IN :ids")
    fun findAllByIds(@Param("ids") ids: List<Long>): List<Avvik>

    @Query("""
        SELECT new no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO(
            k.fylkesLokalID,
            k.kommuneLokalID,
            k.kommunenummer,
            k.kommunenavn,
            CAST(COUNT(a) AS int)
        )
        FROM Avvik a JOIN a.kommuner k
        WHERE a.status IN :statuses
        AND (:grensetyper IS NULL OR a.grensetype IN :grensetyper)
        AND k.kommunenummer IS NOT NULL
        AND k.kommunenavn IS NOT NULL
        GROUP BY k.fylkesLokalID, k.kommuneLokalID, k.kommunenummer, k.kommunenavn
        ORDER BY COUNT(a) DESC
    """)
    fun findKommuneAvvikSummaryPage(
        @Param("statuses") statuses: Collection<AvvikStatus>,
        @Param("grensetyper") grensetyper: List<String>?,
        pageable: Pageable
    ): Page<KommuneAvvikDTO>

}
