package no.kartverket.nibas.nibasarbeidsliste.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.locationtech.jts.geom.LineString
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Entity representing a boundary line from Matrikkelen.
 * Maps to the `matrikkel_grenselinje` table in the database.
 */
@Entity
@Table(name = "matrikkel_grenselinje", schema = "nibas_arbeidsliste_schema")
data class MatrikkelGrenselinje(
    @Id
    @Column(name = "id")
    val id: Long = 0,

    @Column(name = "hjelpelinjetype_id")
    val hjelpelinjetypeId: Short? = null,

    @Column(name = "omtvistet")
    val omtvistet: Boolean = false,

    @Column(name = "folgerterrengdetalj_id")
    val folgerterrengdetaljId: Short? = null,

    @Column(name = "administrativgrensekode_id")
    val administrativgrensekodeId: Short? = null,

    @Column(name = "malemetode_id")
    val malemetodeId: Short? = null,

    @Column(name = "noyaktighet")
    val noyaktighet: Int? = null,

    @Column(name = "datafangstdato")
    val datafangstdato: LocalDate? = null,

    @Column(name = "lagretnoyaktighetsklasse")
    val lagretNoyaktighetsklasse: Short? = null,

    @Column(columnDefinition = "geometry(LineString, 25833)")
    val geom: LineString? = null,

    @Column(name = "oppdateringsdato")
    val oppdateringsdato: LocalDateTime? = null,

    @Column(name = "kommunenr1", length = 4)
    val kommunenr1: String? = null,

    @Column(name = "kommunenr2", length = 4)
    val kommunenr2: String? = null,

    @Column(name = "informasjoncache")
    val informasjoncache: String? = null,

    @Column(name = "versjon")
    val versjon: Long? = null,

    @Column(name = "versjon_id")
    val versjonId: Int? = null,

    @Column(name = "oppdatert_av")
    val oppdatertAv: String? = null
) {
    constructor() : this(id = 0)
}
