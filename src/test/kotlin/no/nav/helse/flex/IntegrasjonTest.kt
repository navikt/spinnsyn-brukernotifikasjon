package no.nav.helse.flex

import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.domene.VedtakStatus
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrasjonTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var brukernotifikasjonRepository: BrukernotifikasjonRepository

    final val fnr = "58229418431"

    @Test
    @Order(100)
    fun `Vedtak status legges på kafka og lagres i db`() {
        val id = "2392jf82jf39jf"

        produceVedtakStatus(id, fnr, VedtakStatus.MOTATT)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`()
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` false
                it.varselId.`should be null`()
            }
    }

    @Test
    @Order(101)
    fun `Vedtaket får status lest og da er vi ferdig`() {
        val id = "2392jf82jf39jf"

        produceVedtakStatus(id, fnr, VedtakStatus.LEST)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`() // TODO: ikke null
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    @Test
    @Order(200)
    fun `Mottar status lest før vi har fått status mottatt`() {
        val id = "034jfi03i04jfjgt"

        produceVedtakStatus(id, fnr, VedtakStatus.LEST)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`() // TODO: ikke null
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    @Test
    @Order(201)
    fun `Får så status mottatt etter at vi allerede har fått status lest`() {
        val id = "034jfi03i04jfjgt"

        produceVedtakStatus(id, fnr, VedtakStatus.MOTATT)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`() // TODO: ikke null
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    @Test
    @Order(300)
    fun `Vi sender done melding for vedtak der oppgave ble opprettet før vi publiserte status`() {
        val id = "93h4uh3wrg"

        produceVedtakStatus(id, fnr, VedtakStatus.LEST)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`() // TODO: ikke null
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    // TODO: Når vi har lagret status mottatt i db, men det var ikke vi som opprettet oppgave
}
