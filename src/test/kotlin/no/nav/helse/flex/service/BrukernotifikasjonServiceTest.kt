package no.nav.helse.flex.service

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.objectMapper
import no.nav.helse.flex.ventPåRecords
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.amshove.kluent.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.HOURS

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class BrukernotifikasjonServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var brukernotifikasjonService: BrukernotifikasjonService

    @Autowired
    private lateinit var brukernotifikasjonRepository: BrukernotifikasjonRepository

    private val start =
        LocalDateTime
            .now()
            .atZone(ZoneId.of("Europe/Oslo"))
            .withHour(0)
            .truncatedTo(HOURS)

    private val kalender = generateSequence(start) { it.plusDays(1) }

    @BeforeAll
    fun tomDatabaseBefore() {
        brukernotifikasjonRepository.deleteAll()
    }

    @AfterAll
    fun tomDatabaseAfter() {
        brukernotifikasjonRepository.deleteAll()
    }

    @Test
    @Order(0)
    fun leggInnEtVedtakSomKanVarsles() {
        val id = "vedtakId"

        produserVedtakStatus(
            id = id,
            fnr = "32132132132",
            status = VedtakStatus.MOTATT,
        ).also { vedtakSkalHaVarsel(id) }

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByOppgaveSendtIsNullAndFerdigIsFalse()
            .shouldHaveSize(1)[0]
    }

    @Test
    @Order(1)
    fun `Sender ikke varsel på lørdag`() {
        val tid = kalender.first { it.dayOfWeek == SATURDAY }
        brukernotifikasjonService.cronJob(tid).shouldBeEqualTo(0)
    }

    @Test
    @Order(1)
    fun `Sender ikke varsel på søndag`() {
        val tid = kalender.first { it.dayOfWeek == SUNDAY }
        brukernotifikasjonService.cronJob(tid).shouldBeEqualTo(0)
    }

    @Test
    @Order(1)
    fun `Sender ikke varsel tidlig i ukedager`() {
        val tid = kalender.first { it.dayOfWeek == MONDAY }.withHour(5)
        brukernotifikasjonService.cronJob(tid).shouldBeEqualTo(0)
    }

    @Test
    @Order(1)
    fun `Sender ikke varsel sent i ukedager`() {
        val tid = kalender.first { it.dayOfWeek == TUESDAY }.withHour(20)
        brukernotifikasjonService.cronJob(tid).shouldBeEqualTo(0)
    }

    @Test
    @Order(1)
    fun `Sender ikke varsel tett opp til grenseverdiene`() {
        val tid = kalender.first { it.dayOfWeek == WEDNESDAY }
        brukernotifikasjonService.cronJob(tid.withHour(9).minusNanos(1)).shouldBeEqualTo(0)
        brukernotifikasjonService.cronJob(tid.withHour(15)).shouldBeEqualTo(0)
    }

    @Test
    @Order(1)
    fun `Sender varsel i fornuftig tidsrom og ukedag`() {
        val tid = kalender.first { it.dayOfWeek == THURSDAY }
        brukernotifikasjonService.cronJob(tid.withHour(12)).shouldBeEqualTo(1)
        varslingConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(2)
    fun `Mottar flere vedtak som skal varsles`() {
        val varslingCronJobTid = kalender.first { it.dayOfWeek == THURSDAY }.withHour(12)

        produserVedtakStatus(
            id = "1",
            fnr = "11111111111",
            status = VedtakStatus.MOTATT,
        ).also { vedtakSkalHaVarsel("1") }
        produserVedtakStatus(
            id = "2",
            fnr = "11111111111",
            status = VedtakStatus.MOTATT,
        ).also { vedtakIkkeSkalHaVarsel("2", varslingCronJobTid) }

        produserVedtakStatus(
            id = "3",
            fnr = "22222222222",
            status = VedtakStatus.MOTATT,
        ).also { vedtakSkalHaVarsel("3") }
        produserVedtakStatus(
            id = "4",
            fnr = "22222222222",
            status = VedtakStatus.MOTATT,
        ).also { vedtakSkalHaVarsel("4") }
        produserVedtakStatus(
            id = "5",
            fnr = "22222222222",
            status = VedtakStatus.MOTATT,
        ).also { vedtakSkalHaVarsel("5") }

        brukernotifikasjonService
            .cronJob(varslingCronJobTid)
            .shouldBeEqualTo(2)

        val vedtakFnr1 =
            brukernotifikasjonRepository
                .findBrukernotifikasjonDbRecordByFnr("11111111111")
                .shouldHaveSize(2)
        val varselIdFnr1 =
            vedtakFnr1
                .first()
                .varselId
                .`should not be null`()
        vedtakFnr1.all { it.varselId == varselIdFnr1 }.`should be true`()

        val vedtakFnr2 =
            brukernotifikasjonRepository
                .findBrukernotifikasjonDbRecordByFnr("22222222222")
                .shouldHaveSize(3)
        val varselIdFnr2 =
            vedtakFnr2
                .first()
                .varselId
                .`should not be null`()
        vedtakFnr2.all { it.varselId == varselIdFnr2 }.`should be true`()

        val oppgaver = varslingConsumer.ventPåRecords(antall = 2)
        varslingConsumer.ventPåRecords(antall = 0)
        val nokkel = oppgaver[0].key()
        nokkel shouldBeEqualTo varselIdFnr1

        val oppgave = oppgaver[0].value().tilOpprettVarselInstance()
        oppgave.type shouldBeEqualTo Varseltype.Oppgave
        oppgave.ident shouldBeEqualTo "11111111111"
        oppgave.sensitivitet shouldBeEqualTo Sensitivitet.High
        oppgave.tekster.first().tekst shouldBeEqualTo "Du har fått svar på søknaden om sykepenger - se vedtaket"
        oppgave.link shouldBeEqualTo "https://localhost"
        oppgave.eksternVarsling.shouldNotBeNull()
        oppgave.eksternVarsling!!.prefererteKanaler shouldBeEqualTo listOf(EksternKanal.SMS)
        oppgave.eksternVarsling!!.smsVarslingstekst shouldBeEqualTo "Hei! Du har fått et vedtak fra Nav. " +
            "Logg inn på Nav for å se svaret. Vennlig hilsen Nav"
    }

    @Test
    @Order(3)
    fun `Mottar nytt vedtak som ikke er gammelt nok for å kunne sende ut brukernotifikasjon`() {
        val varslingCronJobTid = kalender.first { it.dayOfWeek == FRIDAY }.withHour(12)
        produserVedtakStatus(
            id = "6",
            fnr = "33333333333",
            status = VedtakStatus.MOTATT,
        ).also { vedtakIkkeSkalHaVarsel("6", varslingCronJobTid) }

        brukernotifikasjonRepository
            .findBrukernotifikasjonDbRecordByOppgaveSendtIsNullAndFerdigIsFalse()
            .shouldHaveSize(1)

        brukernotifikasjonService
            .cronJob(varslingCronJobTid)
            .shouldBeEqualTo(0)
    }

    private fun vedtakSkalHaVarsel(id: String) {
        val dbRecord = brukernotifikasjonRepository.findByIdOrNull(id)!!
        brukernotifikasjonRepository.save(
            dbRecord.copy(
                mottatt =
                    start
                        .minusHours(brukernotifikasjonService.timerFørVarselKanSendes)
                        .minusHours(1)
                        .toInstant(),
            ),
        )
    }

    private fun vedtakIkkeSkalHaVarsel(
        id: String,
        cronJob: ZonedDateTime,
    ) {
        val dbRecord = brukernotifikasjonRepository.findByIdOrNull(id)!!
        brukernotifikasjonRepository.save(
            dbRecord.copy(
                mottatt = cronJob.toInstant(),
            ),
        )
    }
}

fun String.tilOpprettVarselInstance(): VarselActionBuilder.OpprettVarselInstance = objectMapper.readValue(this)

fun String.tilInaktiverVarselInstance(): VarselActionBuilder.InaktiverVarselInstance = objectMapper.readValue(this)
