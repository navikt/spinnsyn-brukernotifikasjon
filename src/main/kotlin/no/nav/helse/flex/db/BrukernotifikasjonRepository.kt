package no.nav.helse.flex.db

import no.nav.helse.flex.domene.Brukernotifikasjon
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface BrukernotifikasjonRepository : CrudRepository<Brukernotifikasjon, String> {

    @Modifying
    @Query(
        """
            INSERT INTO brukernotifikasjon(ID, FNR, OPPGAVE_SENDT)
            VALUES (:id, :fnr, :oppgaveSendt )
            """
    )
    fun insert(id: String, fnr: String, oppgaveSendt: Instant?)
}
