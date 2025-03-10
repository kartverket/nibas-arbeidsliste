package no.kartverket.nibas.nibasarbeidsliste.model

import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Point
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
import jakarta.persistence.Table

@Entity
@Table(name = "avvik")
data class Avvik(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = true)
    val kommuneNavn: String? = null,

    // En grense i NIBAS slik den så ut i det avviket ble registrert
    @Column(columnDefinition = "geometry(LINESTRING, 25833)")
    val grense: LineString,

    // Liste med punkter som har avvik
    @ElementCollection
    @CollectionTable(name = "avvik_punkter", joinColumns = [JoinColumn(name = "avvik_id")])
    @Column(columnDefinition = "geometry(POINT, 25833)")
    val avvikPunkter: List<Point>,

    @Column(nullable = false)
    val registrertDato: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AvvikStatus = AvvikStatus.NY,

    // Kommunegrense, fylkesgrense, riksgrense, eller territoialgrense
    @Column(nullable = false)
    val grenseType: String
) {
    protected constructor() : this(
        id = 0L,
        kommuneNavn = null,
        grense = GeometryFactory().createLineString(emptyArray()),
        avvikPunkter = emptyList(),
        registrertDato = LocalDateTime.now(),
        status = AvvikStatus.NY,
        grenseType = "UKJENT"
    )
}
