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
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "avvik")
data class Avvik(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // JSON-respons fra NIBAS API for grensen
    @Lob
    @Column(columnDefinition = "TEXT")
    val grenseJson: String? = null,

    @Column(nullable = false)
    val registrertDato: LocalDateTime = LocalDateTime.now(),

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: AvvikStatus = AvvikStatus.NY,

) {
    protected constructor() : this(
        id = 0L,
        grenseJson = null,
        registrertDato = LocalDateTime.now(),
        status = AvvikStatus.NY
    )
}
