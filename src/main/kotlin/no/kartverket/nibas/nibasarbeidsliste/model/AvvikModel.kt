package no.kartverket.nibas.nibasarbeidsliste.model

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import java.time.LocalDate
import java.time.LocalDateTime
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "avvik")
data class Avvik(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val registrertDato: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AvvikStatus = AvvikStatus.NY,

    // Felter fra NIBAS grense
    @Column(name = "grense_id")
    val grenseId: String? = null,

    @Column(name = "lokalid")
    val lokalId: String? = null,

    @Column(name = "grensetype")
    val grensetype: String? = null,

    @Column(name = "geometri", columnDefinition = "geometry(LineString, 25833)")
    val geometri: LineString? = null,

    @Column(name = "gyldig_fra")
    val gyldigFra: LocalDate? = null,

    @Column(name = "gyldig_til")
    val gyldigTil: LocalDate? = null,

    @Column(name = "datafangstdato")
    val datafangstdato: String? = null,

    @Column(name = "foerstedigitaliseringsdato")
    val foerstedigitaliseringsdato: String? = null,

    @Column(name = "opphav")
    val opphav: String? = null,

    @Column(name = "informasjon")
    val informasjon: String? = null,

    @Column(name = "endret_av")
    val endretAv: String? = null,

    @Column(name = "endret_dato")
    val endretDato: String? = null,

    @Column(name = "type_endring")
    val typeEndring: String? = null,

    @Column(name = "maalemetode")
    val maalemetode: String? = null,

    @Column(name = "noeyaktighet")
    val noeyaktighet: Int? = null,

) {
    protected constructor() : this(
        id = 0L,
        grenseId = null,
        lokalId = null,
        grensetype = null,
        geometri = null,
        gyldigFra = null,
        gyldigTil = null,
        datafangstdato = null,
        foerstedigitaliseringsdato = null,
        opphav = null,
        informasjon = null,
        endretAv = null,
        endretDato = null,
        typeEndring = null,
        maalemetode = null,
        noeyaktighet = null,
        registrertDato = LocalDateTime.now(),
        status = AvvikStatus.NY
    )
}
