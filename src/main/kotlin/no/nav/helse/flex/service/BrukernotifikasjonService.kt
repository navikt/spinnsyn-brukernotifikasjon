package no.nav.helse.flex.service

import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.db.BrukernotifikasjonDbRecord
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.kafka.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class BrukernotifikasjonService(
    private val brukernotifikasjonRepository: BrukernotifikasjonRepository,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
    @Value("\${spinnsyn-frontend.url}") private val spinnsynFrontendUrl: String,
) {

    val log = logger()

    fun cronJob() {
        log.info("Kjører cronjob")
    }

    fun sendOppgave(brukernotifikasjonDbRecord: BrukernotifikasjonDbRecord) {
        val id = brukernotifikasjonDbRecord.id
        val fnr = brukernotifikasjonDbRecord.fnr
        val sendtTidspunkt = Instant.now()

        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            Nokkel(serviceuserUsername, id),
            Oppgave(
                sendtTidspunkt.toEpochMilli(),
                fnr,
                id,
                "Du har fått svar på søknaden om sykepenger - se resultatet",
                spinnsynFrontendUrl,
                4,
                true,
                listOf("SMS")
            )
        )

        brukernotifikasjonRepository.save(
            brukernotifikasjonDbRecord.copy(
                oppgaveSendt = sendtTidspunkt,
            )
        )
    }

    fun sendDone(id: String) {
        brukernotifikasjonRepository
            .findByIdOrNull(id)!!
            .let { sendDone(it) }
    }

    fun sendDone(eksisterendeVedtak: BrukernotifikasjonDbRecord) {
        val now = null // TODO: Instant.now()
        log.info("Ville her sendt ut done melding for vedtak ${eksisterendeVedtak.id}")

        /*
        brukernotifikasjonKafkaProdusent.sendDonemelding(
            Nokkel(serviceuserUsername, eksisterendeVedtak.id),
            Done(
                now.toEpochMilli(),
                eksisterendeVedtak.fnr,
                eksisterendeVedtak.id,
            )
        )
        */

        brukernotifikasjonRepository.save(
            eksisterendeVedtak.copy(
                doneSendt = now,
                ferdig = true,
            )
        )
    }
}
