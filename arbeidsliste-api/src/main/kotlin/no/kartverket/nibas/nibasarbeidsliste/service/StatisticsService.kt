package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikStatisticsDTO
import no.kartverket.nibas.nibasarbeidsliste.dto.GrensetypeStatisticsDTO
import no.kartverket.nibas.nibasarbeidsliste.model.AvvikStatus
import no.kartverket.nibas.nibasarbeidsliste.repository.StatisticsRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class StatisticsService(
    private val statisticsRepository: StatisticsRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get statistics about avvik in the system.
     *
     * @return AvvikStatisticsDTO with counts and breakdowns
     */
    fun getAvvikStatistics(): AvvikStatisticsDTO {
        logger.info("Calculating avvik statistics")

        val totalAvvik = statisticsRepository.countTotalAvvik()

        val statusCountsFromDb = statisticsRepository.countByStatus()
            .associate { result ->
                val status = result[0] as AvvikStatus
                val count = (result[1] as Number).toLong()
                status to count
            }

        val statusCounts = AvvikStatus.entries.associateWith { status ->
            statusCountsFromDb[status] ?: 0L
        }

        val arbeidsStatusCountsFromDb = statisticsRepository.countByStatusForArbeidsGrensetype()
            .associate { result ->
                val status = result[0] as AvvikStatus
                val count = (result[1] as Number).toLong()
                status to count
            }

        val arbeidsStatusCounts = AvvikStatus.entries.associateWith { status ->
            arbeidsStatusCountsFromDb[status] ?: 0L
        }

        val borderCounts = statisticsRepository.countBordersByGrensetype()
            .associate { result ->
                val grensetype = result[0] as String
                val count = (result[1] as Number).toLong()
                grensetype to count
            }

        val realPointCounts = statisticsRepository.countRealAvvikPointsByGrensetype()
            .associate { result ->
                val grensetype = result[0] as String
                val count = (result[1] as Number).toLong()
                grensetype to count
            }

        val helperPointCounts = statisticsRepository.countHelperPointsByGrensetype()
            .associate { result ->
                val grensetype = result[0] as String
                val count = (result[1] as Number).toLong()
                grensetype to count
            }

        val allGrensetyper = (borderCounts.keys + realPointCounts.keys + helperPointCounts.keys).distinct()

        val grensetypeDetails = allGrensetyper.associateWith { grensetype ->
            GrensetypeStatisticsDTO(
                antallGrenserMedAvvik = borderCounts[grensetype] ?: 0L,
                antallEkteAvvikPunkter = realPointCounts[grensetype] ?: 0L,
                antallHelperPunkter = helperPointCounts[grensetype] ?: 0L
            )
        }

        val totalHelperPunkter = helperPointCounts.values.sum()
        val arbeidsGrenser = (borderCounts["Kommunegrense"] ?: 0L) + (borderCounts["Fylkesgrense"] ?: 0L)
        val arbeidsAvvikPunkter = (realPointCounts["Kommunegrense"] ?: 0L) + (realPointCounts["Fylkesgrense"] ?: 0L)
        val arbeidsHelperPunkter = (helperPointCounts["Kommunegrense"] ?: 0L) + (helperPointCounts["Fylkesgrense"] ?: 0L)
        val kommunerMedAvvik = statisticsRepository.countDistinctKommunerWithAvvik()

        return AvvikStatisticsDTO(
            totalAvvik = totalAvvik,
            totalHelperPunkter = totalHelperPunkter,
            arbeidsGrenser = arbeidsGrenser,
            arbeidsAvvikPunkter = arbeidsAvvikPunkter,
            arbeidsHelperPunkter = arbeidsHelperPunkter,
            statusCounts = statusCounts,
            arbeidsStatusCounts = arbeidsStatusCounts,
            grensetypeDetails = grensetypeDetails,
            kommunerMedAvvik = kommunerMedAvvik
        )
    }
}