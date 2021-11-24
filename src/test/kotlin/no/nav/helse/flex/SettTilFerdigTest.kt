package no.nav.helse.flex

import no.nav.helse.flex.cronjob.BrukernotifikasjonJob
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import org.amshove.kluent.`should be false`
import org.amshove.kluent.`should be true`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull

class SettTilFerdigTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var brukernotifikasjonRepository: BrukernotifikasjonRepository

    @Autowired
    lateinit var brukernotifikasjonJob: BrukernotifikasjonJob

    @Test
    fun `Setter riktige vedtak til ferdig`() {
        brukernotifikasjonRepository.insert(
            id = "Før",
            fnr = "123",
            ferdig = false,
            mottatt = brukernotifikasjonJob.tidspunkt.minusHours(1).toInstant()
        )
        brukernotifikasjonRepository.insert(
            id = "På",
            fnr = "123",
            ferdig = false,
            mottatt = brukernotifikasjonJob.tidspunkt.toInstant()
        )
        brukernotifikasjonRepository.insert(
            id = "Etter",
            fnr = "123",
            ferdig = false,
            mottatt = brukernotifikasjonJob.tidspunkt.plusHours(1).toInstant()
        )

        brukernotifikasjonJob.run()

        brukernotifikasjonRepository.findByIdOrNull("Før")!!.ferdig.`should be true`()
        brukernotifikasjonRepository.findByIdOrNull("På")!!.ferdig.`should be true`()
        brukernotifikasjonRepository.findByIdOrNull("Etter")!!.ferdig.`should be false`()
    }
}
