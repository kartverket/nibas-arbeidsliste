package no.kartverket.nibas.nibasarbeidsliste.service

import no.kartverket.nibas.nibasarbeidsliste.dto.AvvikDTO
import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import no.kartverket.nibas.nibasarbeidsliste.repository.AvvikRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AvvikService(
    private val avvikRepository: AvvikRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun hentAlleAvvik(): List<AvvikDTO> {
        logger.info("Henter alle avvik")
        return avvikRepository.findAll().map { it.tilDTO() }
    }

    private fun Avvik.tilDTO(): AvvikDTO {
        return AvvikDTO(
            id = id,
            registrertDato = registrertDato,
            status = status,
            harGrenseJson = grenseJson != null,
            grenseJson = grenseJson
        )
    }
}
