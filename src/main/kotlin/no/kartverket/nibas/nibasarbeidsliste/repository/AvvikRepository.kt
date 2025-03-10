package no.kartverket.nibas.nibasarbeidsliste.repository

import no.kartverket.nibas.nibasarbeidsliste.model.Avvik
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AvvikRepository : JpaRepository<Avvik, Long>
