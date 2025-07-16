package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.model.Kommune
import no.kartverket.nibas.nibasarbeidsliste.model.KoordinaterMedAvvik
import no.kartverket.nibas.nibasarbeidsliste.model.MatrikkelGrenselinje
import no.kartverket.nibas.nibasarbeidsliste.model.NibasGeometri
import no.kartverket.nibas.nibasarbeidsliste.model.NibasGrense
import no.kartverket.nibas.nibasarbeidsliste.model.NibasKommune
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import no.kartverket.nibas.nibasarbeidsliste.repository.MatrikkelGrenselinjeRepository
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.index.strtree.STRtree
import org.locationtech.jts.operation.distance.DistanceOp
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.math.roundToInt
import kotlin.time.measureTime


@Service
class GrenseSammenlignerService(
    private val matrikkelRepo: MatrikkelGrenselinjeRepository,
    private val nibasService: NibasGrenseService,
    private val avvikRepository: AvvikRepository
) {
    companion object {
        private val log = LoggerFactory.getLogger(GrenseSammenlignerService::class.java)
    }

    fun finnAvvik(toleranseMeter: Double): GrenseSammenligningResultat {
        // 1. Hent totalt antall NIBAS grenser først
        log.info("Henter totalt antall NIBAS grenser...")
        val initialResponse = nibasService.getGrenser(size = 1)
        val totaltAntallGrenser = initialResponse.totaltAntall

        log.info("Totalt $totaltAntallGrenser NIBAS grenser funnet")

        // 2. Hent alle NIBAS grenser
        // NIBAS grenser are stored in UTM33 in nibas database.
        log.info("\nHenter alle NIBAS grenser...")
        val nibasGrenser = nibasService.getGrenser(size = totaltAntallGrenser).innhold
        log.info("Fant ${nibasGrenser.size} NIBAS grenser")

        // 3. Hent M22 grenser
        // M22 grenser er lagret som UTM33 i nibas database.
        log.info("\nHenter M22 grenser...")
        val m22Grenser = matrikkelRepo.findAll()
        log.info("Fant ${m22Grenser.size} M22 grenser")

        // 4. Sammenlign og lagre avvik
        var resultat: SammenligningInternResultat
        val totalTime = measureTime {
            resultat = sammenlignGrenser(nibasGrenser, m22Grenser, toleranseMeter)
        }

        return GrenseSammenligningResultat(
            antallGrenserSjekket = nibasGrenser.size,
            antallGrenserMedAvvik = resultat.antallGrenserMedAvvik,
            totaltAntallAvvik = resultat.totaltAntallAvvik,
            toleranseMetreBrukt = toleranseMeter,
            antallAvvikLagret = resultat.antallAvvikLagret,
            tidsbrukMs = totalTime.inWholeMilliseconds
        )
    }

    private data class GrenseResultat(val grenseId: String, val avvikPunkter: MutableList<AvvikPunkt> = mutableListOf())

    private data class AvvikPunkt(
        val nibasKoordinat: Coordinate,
        val closestM22Koordinat: Coordinate?,
        val distanse: Double,
        val erPaaMatrikkelLinje: Boolean
    )

    private fun sammenlignGrenser(
        nibasGrenser: List<NibasGrense>,
        m22Grenser: List<MatrikkelGrenselinje>,
        toleranseMeter: Double
    ): SammenligningInternResultat {
        val geometryFactory = GeometryFactory()

        fun nibasGeometriToLineString(geom: NibasGeometri): LineString {
            val coordinates = geom.coordinates.map { Coordinate(it[0], it[1]) }.toTypedArray()
            return geometryFactory.createLineString(coordinates)
        }

        val m22LineIndex = STRtree()
        m22Grenser.forEach { m22 ->
            m22.geom?.let { line -> m22LineIndex.insert(line.envelopeInternal, line) }
        }
        m22LineIndex.build()

        log.info("\nProsesserer ${nibasGrenser.size} NIBAS grenser mot ${m22Grenser.size} M22 grenser...")

        val grenseResultater = mutableMapOf<String, GrenseResultat>()
        var totalAvvik = 0

        for ((grenseIndex, nibasGrense) in nibasGrenser.withIndex()) {
            val nibasLine = nibasGeometriToLineString(nibasGrense.geometri)

            if ((grenseIndex + 1) % 100 == 0 || grenseIndex == nibasGrenser.size - 1) {
                log.info("Progress: ${grenseIndex + 1}/${nibasGrenser.size} grenser sjekket, $totalAvvik avvik funnet så langt...")
            }

            @Suppress("UNCHECKED_CAST")
            val candidateM22Lines = m22LineIndex.query(nibasLine.envelopeInternal) as List<LineString>

            if (candidateM22Lines.isEmpty()) {
                continue
            }

            for (nibasCoord in nibasLine.coordinates) {
                val nibasPoint = geometryFactory.createPoint(nibasCoord)

                var closestDistance = Double.MAX_VALUE
                var closestM22Coord: Coordinate? = null

                for (m22Line in candidateM22Lines) {
                    val tempClosestPoint = DistanceOp.nearestPoints(m22Line, nibasPoint)
                    val distance = tempClosestPoint[0].distance(tempClosestPoint[1])

                    if (distance < closestDistance) {
                        closestDistance = distance
                        val rawCoord = tempClosestPoint[0]
                        closestM22Coord = Coordinate(
                            (rawCoord.x * 100.0).roundToInt() / 100.0,
                            (rawCoord.y * 100.0).roundToInt() / 100.0
                        )
                    }
                }

                if (closestDistance > toleranseMeter) {
                    val grenseResultat = grenseResultater.getOrPut(nibasGrense.id) { GrenseResultat(nibasGrense.id) }
                    grenseResultat.avvikPunkter.add(AvvikPunkt(
                        nibasKoordinat = nibasCoord,
                        closestM22Koordinat = closestM22Coord,
                        distanse = closestDistance,
                        erPaaMatrikkelLinje = false
                    ))
                    totalAvvik++
                }
            }
        }

        // Create Avvik objects for all found deviations with default status NY
        val calculatedAvviks = grenseResultater.values.mapNotNull { resultat ->
            val nibasGrense = nibasGrenser.find { it.id == resultat.grenseId } ?: return@mapNotNull null
            if (resultat.avvikPunkter.isEmpty()) return@mapNotNull null

            val nibasLine = nibasGeometriToLineString(nibasGrense.geometri)
            val koordinaterMedAvvik = resultat.avvikPunkter.map { avvikPunkt ->
                KoordinaterMedAvvik(
                    koordinatFraNibas = geometryFactory.createPoint(avvikPunkt.nibasKoordinat),
                    koordinatFraMatrikkelen = avvikPunkt.closestM22Koordinat?.let { geometryFactory.createPoint(it) },
                    distanseMellomKoordinater = avvikPunkt.distanse,
                    erPaaMatrikkelLinje = avvikPunkt.erPaaMatrikkelLinje
                )
            }

            Avvik(
                grenseId = nibasGrense.id,
                lokalId = nibasGrense.lokalid,
                status = AvvikStatus.NY,
                grensetype = nibasGrense.grensetype,
                geometri = nibasLine,
                gyldigFra = nibasGrense.gyldighet.gyldigFra,
                gyldigTil = nibasGrense.gyldighet.gyldigTil,
                datafangstdato = nibasGrense.datafangstdato?.toString(),
                foerstedigitaliseringsdato = nibasGrense.foerstedigitaliseringsdato.toString(),
                opphav = nibasGrense.opphav,
                informasjon = nibasGrense.informasjon,
                endretAv = nibasGrense.endretAv,
                endretDato = nibasGrense.endretDato.toString(),
                typeEndring = nibasGrense.typeEndring,
                maalemetode = nibasGrense.maalemetode,
                noeyaktighet = nibasGrense.noeyaktighet,
                kommuner = convertKommuner(nibasGrense.kommuner),
                antallKoordinater = nibasLine.numPoints,
                antallKoordinaterMedAvvik = resultat.avvikPunkter.size,
                koordinaterMedAvvik = koordinaterMedAvvik,
                tolerance = toleranseMeter
            )
        }

        // Fetch existing deviations and decide what to do
        val existingAvvikMap = avvikRepository.findAll().associateBy { it.lokalId }
        val avvikToSave = mutableListOf<Avvik>()

        for (calculatedAvvik in calculatedAvviks) {
            val existingAvvik = existingAvvikMap[calculatedAvvik.lokalId]

            if (existingAvvik == null) {
                avvikToSave.add(calculatedAvvik)
            } else {
                // A deviation for this lokalId already exists. Check its status.
                when (existingAvvik.status) {
                    AvvikStatus.FIKSET, AvvikStatus.AVVIST -> {
                        // This is a regression. Create a new deviation record.
                        log.warn("REGRESJON funnet for lokalId: ${existingAvvik.lokalId}. Status var ${existingAvvik.status}, lager nytt avvik.")
                        avvikToSave.add(calculatedAvvik)
                    }

                    AvvikStatus.NY, AvvikStatus.UNDER_BEHANDLING, AvvikStatus.VENT -> {
                        // This is an already active deviation. Do nothing.
                        log.info("Hopper over allerede aktivt avvik for lokalId: ${existingAvvik.lokalId} (status: ${existingAvvik.status})")
                    }
                }
            }
        }

        val savedAvvik = avvikRepository.saveAll(avvikToSave)
        log.info("Lagret ${savedAvvik.size} nye til database")

        return SammenligningInternResultat(
            antallGrenserMedAvvik = grenseResultater.count { it.value.avvikPunkter.isNotEmpty() },
            totaltAntallAvvik = totalAvvik,
            antallAvvikLagret = savedAvvik.size
        )
    }

    private fun convertKommuner(nibasKommuner: List<NibasKommune>): List<Kommune> {
        return nibasKommuner.map { nibasKommune ->
            Kommune(
                fylkesLokalID = nibasKommune.fylkesLokalID,
                kommuneLokalID = nibasKommune.kommuneLokalID,
                kommunenummer = nibasKommune.kommunenummer,
                kommunenavn = nibasKommune.kommunenavn
            )
        }
    }
}

data class GrenseSammenligningResultat(
    val antallGrenserSjekket: Int,
    val antallGrenserMedAvvik: Int,
    val totaltAntallAvvik: Int,
    val toleranseMetreBrukt: Double,
    val antallAvvikLagret: Int,
    val tidsbrukMs: Long
)

data class SammenligningInternResultat(
    val antallGrenserMedAvvik: Int,
    val totaltAntallAvvik: Int,
    val antallAvvikLagret: Int
)
