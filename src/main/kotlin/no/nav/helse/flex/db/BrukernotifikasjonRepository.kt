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
            INSERT INTO brukernotifikasjon(VEDTAKSID, GRUPPERINGSID, FNR, OPPGAVE_SENDT)
            VALUES (:vedtaksid, :grupperingsid, :fnr, :oppgaveSendt )
            """
    )
    fun insert(vedtaksid: String, grupperingsid: String, fnr: String, oppgaveSendt: Instant?)
}
