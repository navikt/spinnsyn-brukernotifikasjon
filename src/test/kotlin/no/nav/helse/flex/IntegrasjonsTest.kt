package no.nav.helse.flex

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.service.BrukernotifikasjonService
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
            .shouldBe(1)
        varslingConsumer.ventPåRecords(antall = 1)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id shouldBe id
                it.fnr shouldBe fnr
                it.oppgaveSendt.shouldNotBeNull()
                it.doneSendt.shouldBeNull()
                it.mottatt.shouldNotBeNull()
                it.ferdig shouldBe false
                it.varselId.shouldNotBeNull()
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
                it.id shouldBe id
                it.fnr shouldBe fnr
                it.oppgaveSendt.shouldNotBeNull()
                it.doneSendt.shouldNotBeNull()
                it.mottatt.shouldNotBeNull()
                it.ferdig shouldBe true
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
                it.id shouldBe id
                it.fnr shouldBe fnr
                it.oppgaveSendt.shouldBeNull()
                it.doneSendt.shouldBeNull()
                it.mottatt.shouldNotBeNull()
                it.ferdig shouldBe true
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
                it.id shouldBe id
                it.fnr shouldBe fnr
                it.oppgaveSendt.shouldBeNull()
                it.doneSendt.shouldBeNull()
                it.mottatt.shouldNotBeNull()
                it.ferdig shouldBe true
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
            .shouldBe(0)

        produserVedtakStatus(id, fnr, VedtakStatus.LEST)

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByFnr(fnr)
            .first { it.id == id }
            .also {
                it.id shouldBe id
                it.fnr shouldBe fnr
                it.oppgaveSendt.shouldBeNull()
                it.doneSendt.shouldBeNull()
                it.mottatt.shouldNotBeNull()
                it.ferdig shouldBe true
            }
    }
}
