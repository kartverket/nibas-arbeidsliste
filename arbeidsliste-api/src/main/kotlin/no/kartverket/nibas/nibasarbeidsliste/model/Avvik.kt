package no.kartverket.nibas.nibasarbeidsliste.model

import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
import java.time.LocalDate
import java.time.LocalDateTime
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table


@Entity
@Table(name = "avvik", schema = "nibas_arbeidsliste_schema")
data class Avvik(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // Dato avvik ble registrert
    @Column(nullable = false)
    val registrertDato: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AvvikStatus = AvvikStatus.NY,

    //-------- Felter fra sammelignig ----------

    // Totalt antall koordinater
    @Column(name = "antall_koordinater")
    val antallKoordinater: Int? = null,

    // Antall koordinater med avvik
    @Column(name = "antall_koordinater_med_avvik")
    val antallKoordinaterMedAvvik: Int? = null,

    // Liste med koordinater som har avvik
    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(
        name = "koordinater_med_avvik",
        schema = "nibas_arbeidsliste_schema",
        joinColumns = [JoinColumn(name = "avvik_id")]
    )
    val koordinaterMedAvvik: List<KoordinaterMedAvvik>? = null,

    // tolerance
    @Column(nullable = false)
    val tolerance: Double? = null,


    //-------- Felter fra NIBAS grense ----------
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

    @ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
    @CollectionTable(
        name = "grense_kommuner",
        schema = "nibas_arbeidsliste_schema",
        joinColumns = [JoinColumn(name = "avvik_id")]
    )
    val kommuner: List<Kommune>? = null,

    ) {
    private constructor() : this(
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

@Embeddable
data class KoordinaterMedAvvik(
    @Column(name = "koordinat_fra_nibas", columnDefinition = "geometry(Point, 25833)")
    val koordinatFraNibas: Point?,

    @Column(name = "koordinat_fra_matrikkelen", columnDefinition = "geometry(Point, 25833)")
    val koordinatFraMatrikkelen: Point?,

    @Column(name = "distanse_mellom_koordinater")
    val distanseMellomKoordinater: Double? = null,

    @Column(name = "er_paa_matrikkel_linje")
    val erPaaMatrikkelLinje: Boolean? = null,
) {
    private constructor() : this(
        koordinatFraNibas = null,
        koordinatFraMatrikkelen = null,
        distanseMellomKoordinater = null,
        erPaaMatrikkelLinje = null
    )
}

@Embeddable
data class Kommune(
    @Column(name = "fylkes_lokalid")
    val fylkesLokalID: String? = null,

    @Column(name = "kommune_lokalid")
    val kommuneLokalID: String? = null,

    @Column(name = "kommunenummer")
    val kommunenummer: String? = null,

    @Column(name = "kommunenavn")
    val kommunenavn: String? = null
) {
    private constructor() : this(
        fylkesLokalID = null,
        kommuneLokalID = null,
        kommunenummer = null,
        kommunenavn = null
    )
}
