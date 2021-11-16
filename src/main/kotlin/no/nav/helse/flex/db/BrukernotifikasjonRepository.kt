package no.nav.helse.flex.db

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface BrukernotifikasjonRepository : CrudRepository<BrukernotifikasjonDbRecord, String> {

    @Modifying
    @Query(
        """
            INSERT INTO brukernotifikasjon(ID, FNR, OPPGAVE_SENDT)
            VALUES (:id, :fnr, :oppgaveSendt )
            """
    )
    fun insert(id: String, fnr: String, oppgaveSendt: Instant?)
    fun findBrukernotifikasjonDbRecordById(id: String): BrukernotifikasjonDbRecord?
}

@Table("brukernotifikasjon")
data class BrukernotifikasjonDbRecord(
    @Id
    val id: String,
    val fnr: String,
    val oppgaveSendt: Instant? = null,
    val doneSendt: Instant? = null,
    val oppdatert: Instant? = Instant.now(),
    val planlagtSendes: Instant,
)
