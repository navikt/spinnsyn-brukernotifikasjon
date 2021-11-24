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
        UPDATE brukernotifikasjon
        SET ferdig = true
        WHERE mottatt <= :tidspunkt
        AND ferdig = false
        """
    )
    fun settTilFerdigFørtidspunkt(tidspunkt: Instant): Int

    @Modifying
    @Query(
        """
        INSERT INTO brukernotifikasjon(ID, FNR, FERDIG, MOTTATT) 
        VALUES (:id, :fnr, :ferdig, :mottatt)
        """
    )
    fun insert(id: String, fnr: String, ferdig: Boolean, mottatt: Instant)

    @Modifying
    @Query(
        """
        UPDATE brukernotifikasjon 
        SET ferdig = true 
        WHERE id = :id
        """
    )
    fun settTilFerdig(id: String)

    fun findBrukernotifikasjonDbRecordByFnr(fnr: String): List<BrukernotifikasjonDbRecord>
}

@Table("brukernotifikasjon")
data class BrukernotifikasjonDbRecord(
    @Id
    val id: String,
    val fnr: String,
    val oppgaveSendt: Instant? = null,
    val doneSendt: Instant? = null,
    val ferdig: Boolean,
    val mottatt: Instant,
    val varselId: String? = null,
)
