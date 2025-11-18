package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneAvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.KommuneParAvvikDTO
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

    @Query("SELECT a FROM Avvik a JOIN a.kommuner k WHERE k.kommuneLokalID= :lokalid AND a.grensetype IN :grensetyper")
    fun findKommuneByLokalIdAndGrensetyper(@Param("lokalid") lokalid: String, @Param("grensetyper") grensetyper: List<String>): List<Avvik>

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
            CAST(COUNT(DISTINCT a.id) AS int)
        )
        FROM Avvik a JOIN a.kommuner k
        WHERE a.status IN :statuses
        AND (:grensetyper IS NULL OR a.grensetype IN :grensetyper)
        AND k.kommunenummer IS NOT NULL
        AND k.kommunenavn IS NOT NULL
        GROUP BY k.fylkesLokalID, k.kommuneLokalID, k.kommunenummer, k.kommunenavn
        ORDER BY COUNT(DISTINCT a.id) DESC
    """)
    fun findKommuneAvvikSummaryPage(
        @Param("statuses") statuses: Collection<AvvikStatus>,
        @Param("grensetyper") grensetyper: List<String>?,
        pageable: Pageable
    ): Page<KommuneAvvikDTO>

    @Query("""
        SELECT new no.kartverket.nibas.nibasarbeidsliste.dto.KommuneParAvvikDTO(
            new no.kartverket.nibas.nibasarbeidsliste.dto.KommuneDTO(
                k1.fylkesLokalID,
                k1.kommuneLokalID,
                k1.kommunenummer,
                k1.kommunenavn
            ),
            new no.kartverket.nibas.nibasarbeidsliste.dto.KommuneDTO(
                k2.fylkesLokalID,
                k2.kommuneLokalID,
                k2.kommunenummer,
                k2.kommunenavn
            ),
            CAST(COUNT(DISTINCT a.id) AS int),
            CAST(COALESCE(SUM(a.antallKoordinaterMedAvvik), 0) AS int)
        )
        FROM Avvik a
        JOIN a.kommuner k1
        JOIN a.kommuner k2
        WHERE k1.kommunenummer < k2.kommunenummer
        AND a.status IN :statuses
        AND (:grensetyper IS NULL OR a.grensetype IN :grensetyper)
        AND k1.kommunenummer IS NOT NULL
        AND k2.kommunenummer IS NOT NULL
        AND k1.kommunenavn IS NOT NULL
        AND k2.kommunenavn IS NOT NULL
        GROUP BY k1.fylkesLokalID, k1.kommuneLokalID, k1.kommunenummer, k1.kommunenavn,
                 k2.fylkesLokalID, k2.kommuneLokalID, k2.kommunenummer, k2.kommunenavn
        ORDER BY COUNT(DISTINCT a.id) DESC
    """)
    fun findKommuneParAvvikSummaryPage(
        @Param("statuses") statuses: Collection<AvvikStatus>,
        @Param("grensetyper") grensetyper: List<String>?,
        pageable: Pageable
    ): Page<KommuneParAvvikDTO>

    @Query("""
        SELECT a FROM Avvik a
        JOIN a.kommuner k1
        JOIN a.kommuner k2
        WHERE ((k1.kommuneLokalID = :lokalId1 AND k2.kommuneLokalID = :lokalId2)
            OR (k1.kommuneLokalID = :lokalId2 AND k2.kommuneLokalID = :lokalId1))
        AND (:grensetyper IS NULL OR a.grensetype IN :grensetyper)
    """)
    fun findByKommunePar(
        @Param("lokalId1") lokalId1: String,
        @Param("lokalId2") lokalId2: String,
        @Param("grensetyper") grensetyper: List<String>?
    ): List<Avvik>

}
