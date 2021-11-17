package no.nav.helse.flex.service

import no.nav.helse.flex.db.BrukernotifikasjonDbRecord
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.domene.tilVedtakStatus
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.EnvironmentToggles
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakStatusService(
    private val environmentToggles: EnvironmentToggles,
    private val brukernotifikasjonRepository: BrukernotifikasjonRepository,
) {
    private val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        cr.value()
            .tilVedtakStatus()
            .takeIf { environmentToggles.isNotProduction() }
            ?.apply {
                brukernotifikasjonRepository
                    .findByIdOrNull(id)
                    ?.let { behandleEksisterendeVedtak(it) }
                    ?: behandleNyttVedtak()
            }
            ?: log.info("Mottok vedtak status for id: ${cr.key()}")
    }

    private fun VedtakStatusDTO.behandleNyttVedtak() {
        brukernotifikasjonRepository.insert(
            id = id,
            fnr = fnr,
            ferdig = vedtakStatus == VedtakStatus.LEST,
            mottatt = Instant.now(),
        )
        log.info("Oppdaget ny vedtak status for id: $id")
    }

    private fun VedtakStatusDTO.behandleEksisterendeVedtak(
        eksisterendeVedtak: BrukernotifikasjonDbRecord
    ) {
        if (eksisterendeVedtak.ferdig || vedtakStatus == VedtakStatus.MOTATT) {
            log.warn("Vedtak med status $vedtakStatus og id $id er allerede behandlet!")
            return
        }

        if (eksisterendeVedtak.oppgaveSendt != null) {
            // TODO: Send done melding og sett doneSendt i databasen
            log.info("Ville her sendt ut done melding for vedtak $id")
        }

        brukernotifikasjonRepository.settTilFerdig(eksisterendeVedtak.id)

        log.info("Vedtak med id: $id er nå ferdig behandlet")
    }
}
