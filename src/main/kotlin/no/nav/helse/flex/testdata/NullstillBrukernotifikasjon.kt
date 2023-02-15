package no.nav.helse.flex.testdata

import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("testdatareset")
class NullstillBrukernotifikasjon(
    private val brukernotifikasjonService: BrukernotifikasjonService,
    private val brukernotifikasjonRepository: BrukernotifikasjonRepository,
) {
    private val log = logger()

    fun nullstill(fnr: String): Int {
        var antall = 0

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .forEach {
                if (it.oppgaveSendt != null) {
                    log.info("Sender done melding med varsel id ${it.varselId} for vedtak ${it.id}")
                    brukernotifikasjonService.sendDone(it)
                }
                brukernotifikasjonRepository.deleteById(it.id)
                antall++
            }

        return antall
    }
}
