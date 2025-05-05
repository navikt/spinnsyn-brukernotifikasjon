package no.nav.helse.flex

import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrasjonsTest : FellesTestOppsett() {
    @Autowired
    lateinit var brukernotifikasjonRepository: BrukernotifikasjonRepository

    @Autowired
    lateinit var brukernotifikasjonService: BrukernotifikasjonService

    final val fnr = "58229418431"

    @Test
    @Order(100)
    fun `Vedtak status legges på kafka og lagres i db`() {
        val id = "2392jf82jf39jf"

        produserVedtakStatus(id, fnr, VedtakStatus.MOTATT)

        brukernotifikasjonService
            .cronJob(tidspunktVarselKanSendesUt())
            .shouldBeEqualTo(1)
        varslingConsumer.ventPåRecords(antall = 1)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should not be null`()
                it.doneSendt.`should be null`()
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` false
                it.varselId.`should not be null`()
            }
    }

    @Test
    @Order(101)
    fun `Vedtaket får status lest og da er vi ferdig`() {
        val id = "2392jf82jf39jf"

        produserVedtakStatus(id, fnr, VedtakStatus.LEST)
        varslingConsumer.ventPåRecords(antall = 1)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should not be null`()
                it.doneSendt.`should not be null`()
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    @Test
    @Order(200)
    fun `Mottar status lest før vi har fått status mottatt`() {
        val id = "034jfi03i04jfjgt"

        produserVedtakStatus(id, fnr, VedtakStatus.LEST)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`()
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    @Test
    @Order(201)
    fun `Får så status mottatt etter at vi allerede har fått status lest`() {
        val id = "034jfi03i04jfjgt"

        produserVedtakStatus(id, fnr, VedtakStatus.MOTATT)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`()
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }

    @Test
    @Order(300)
    fun `Sender ikke done melding når vi ikke har lagd brukernotifikasjonen`() {
        val id = "93h4uh3wrg"
        produserVedtakStatus(id, fnr, VedtakStatus.MOTATT)

        brukernotifikasjonRepository.settTilFerdig(id)
        brukernotifikasjonService
            .cronJob(tidspunktVarselKanSendesUt())
            .shouldBeEqualTo(0)

        produserVedtakStatus(id, fnr, VedtakStatus.LEST)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id `should be equal to` id
                it.fnr `should be equal to` fnr
                it.oppgaveSendt.`should be null`()
                it.doneSendt.`should be null`()
                it.mottatt.`should not be null`()
                it.ferdig `should be equal to` true
            }
    }
}
