package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import org.junit.jupiter.api.Test
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.PrecisionModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@DataJpaTest
@ActiveProfiles("localhost", "security-off")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.jpa.hibernate.ddl-auto=update",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.show-sql=true"
])
class AvvikRepositoryTest {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Autowired
    private lateinit var avvikRepository: AvvikRepository

    private val geometryFactory = GeometryFactory(PrecisionModel(), 25833)

    @Test
    fun `test lagre og hente avvik`() {
        // Opprett LineString for grense
        val grenseCoordinates = arrayOf(
            Coordinate(10.0, 60.0),
            Coordinate(10.1, 60.1),
            Coordinate(10.2, 60.2)
        )
        val grense = geometryFactory.createLineString(grenseCoordinates)

        // Opprett Points for avvikspunkter
        val avvikPunkt1 = geometryFactory.createPoint(Coordinate(10.1, 60.1))
        val avvikPunkt2 = geometryFactory.createPoint(Coordinate(10.15, 60.15))

        // Opprett Avvik-objekt
        val avvik = Avvik(
            kommuneNavn = "Oslo",
            grense = grense,
            avvikPunkter = listOf(avvikPunkt1, avvikPunkt2),
            status = AvvikStatus.NY,
            grenseType = "KOMMUNEGRENSE"
        )

        // Lagre avvik
        val lagretAvvik = avvikRepository.save(avvik)
        logger.info("Lagret avvik med ID: ${lagretAvvik.id}")

        // Hent avvik fra databasen
        val hentetAvvik = avvikRepository.findById(lagretAvvik.id!!).orElse(null)

        // Verifiser at avviket ble lagret og hentet korrekt
        assertNotNull(hentetAvvik)
        assertEquals("Oslo", hentetAvvik.kommuneNavn)
        assertEquals(AvvikStatus.NY, hentetAvvik.status)
        assertEquals("KOMMUNEGRENSE", hentetAvvik.grenseType)

        // Verifiser geometri
        assertEquals(3, hentetAvvik.grense.numPoints)
        assertEquals(2, hentetAvvik.avvikPunkter.size)
        assertEquals(10.1, hentetAvvik.avvikPunkter[0].x, 0.001)
        assertEquals(60.1, hentetAvvik.avvikPunkter[0].y, 0.001)
        assertEquals(10.15, hentetAvvik.avvikPunkter[1].x, 0.001)
        assertEquals(60.15, hentetAvvik.avvikPunkter[1].y, 0.001)

        logger.info("Test fullført: Avvik ble lagret og hentet korrekt")
    }
}
