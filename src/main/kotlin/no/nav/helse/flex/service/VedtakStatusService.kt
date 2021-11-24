package no.nav.helse.flex.service

import no.nav.helse.flex.db.BrukernotifikasjonDbRecord
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.domene.tilVedtakStatus
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class VedtakStatusService(
    private val brukernotifikasjonRepository: BrukernotifikasjonRepository,
    private val brukernotifikasjonService: BrukernotifikasjonService,
) {
    private val log = logger()

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        cr.value()
            .tilVedtakStatus()
            .apply {
                brukernotifikasjonRepository
                    .findByIdOrNull(id)
                    ?.let { behandleEksisterendeVedtak(it) }
                    ?: behandleNyttVedtak()
            }
    }

    private fun VedtakStatusDTO.behandleNyttVedtak() {
        brukernotifikasjonRepository.insert(
            id = id,
            fnr = fnr,
            ferdig = false,
            mottatt = Instant.now(),
        )
        log.info("Oppdaget ny vedtak status for id: $id")


        if (vedtakStatus == VedtakStatus.LEST) {
            brukernotifikasjonService.sendDone(id)
        }
    }

    private fun VedtakStatusDTO.behandleEksisterendeVedtak(
        eksisterendeVedtak: BrukernotifikasjonDbRecord
    ) {
        if (eksisterendeVedtak.ferdig || vedtakStatus == VedtakStatus.MOTATT) {
            log.info("Vedtak med status $vedtakStatus og id $id er allerede behandlet!")
            return
        }

        if (eksisterendeVedtak.oppgaveSendt != null) {
            brukernotifikasjonService.sendDone(eksisterendeVedtak)
        }

        brukernotifikasjonRepository.settTilFerdig(eksisterendeVedtak.id)

        log.info("Vedtak med id: $id er nå ferdig behandlet")
    }
}
