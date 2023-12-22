package no.nav.helse.flex.service

import no.nav.brukernotifikasjon.schemas.builders.DoneInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.NokkelInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.OppgaveInputBuilder
import no.nav.brukernotifikasjon.schemas.builders.domain.PreferertKanal
import no.nav.helse.flex.db.BrukernotifikasjonDbRecord
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.kafka.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URL
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

@Service
class BrukernotifikasjonService(
    private val brukernotifikasjonRepository: BrukernotifikasjonRepository,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    private val metrikk: Metrikk,
    @Value("\${spinnsyn-frontend.url}") private val spinnsynFrontendUrl: String,
) {
    val log = logger()
    val timerFørVarselKanSendes = 1L

    // Må være i UTC siden det forventes av Brukernotfikasjoner.
    fun cronJob(now: ZonedDateTime = Instant.now().atZone(ZoneOffset.UTC)): Int {
        var antall = 0

        if (now.erFornuftigTidspunktForVarsling()) {
            val venteperiode = now.minusHours(timerFørVarselKanSendes).toInstant()

            log.info("Kjører cronjob")

            brukernotifikasjonRepository
                .findBrukernotifikasjonDbRecordByOppgaveSendtIsNullAndFerdigIsFalse()
                .groupBy { it.fnr }
                .filter { (_, brukerSineVedtak) ->
                    brukerSineVedtak.any { it.mottatt.isBefore(venteperiode) }
                }
                .forEach { (_, brukerSineVedtak) ->
                    sendOppgave(brukerSineVedtak)
                    antall++
                }
        }

        return antall
    }

    @Transactional
    fun sendOppgave(brukerSineVedtak: List<BrukernotifikasjonDbRecord>) {
        val varselId = UUID.randomUUID().toString()
        val fnr = brukerSineVedtak.first().fnr
        val sendtTidspunkt = LocalDateTime.now() // Brukernotifikasjon forventer at sendtTidspunkt settes til UTC

        brukerSineVedtak.forEach {
            brukernotifikasjonRepository.settVarselId(
                varselId = varselId,
                sendt = sendtTidspunkt.atZone(ZoneId.of("Europe/Oslo")).toInstant(),
                id = it.id,
            )
        }

        metrikk.brukernotifikasjonSendt(brukerSineVedtak.size)

        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            skapNokkel(varselId, fnr),
            OppgaveInputBuilder()
                .withTidspunkt(sendtTidspunkt)
                .withTekst("Du har fått svar på søknaden om sykepenger - se vedtaket")
                .withSmsVarslingstekst("Hei! Du har fått et vedtak fra NAV. Logg inn på NAVs nettsider for å se svaret. Mvh NAV")
                .withLink(URL(spinnsynFrontendUrl))
                .withSikkerhetsnivaa(4)
                .withEksternVarsling(true)
                .withPrefererteKanaler(PreferertKanal.SMS)
                .build(),
        )

        log.info("Sendte brukernotifikasjon med varsel id $varselId for vedtak ${brukerSineVedtak.map { it.id }}")
    }

    @Transactional
    fun sendDone(eksisterendeVedtak: BrukernotifikasjonDbRecord) {
        val now = LocalDateTime.now()

        brukernotifikasjonRepository
            .findByIdOrNull(eksisterendeVedtak.id)
            ?.let {
                val varselId = it.varselId!!
                val fnr = it.fnr

                brukernotifikasjonRepository.settTilFerdigMedVarselId(
                    varselId = varselId,
                    sendt = now.atZone(ZoneId.of("Europe/Oslo")).toInstant(),
                )

                metrikk.brukernotifikasjonDone()

                brukernotifikasjonKafkaProdusent.sendDonemelding(
                    skapNokkel(varselId, fnr),
                    DoneInputBuilder()
                        .withTidspunkt(now)
                        .build(),
                )
            }
            ?: throw RuntimeException("Kan ikke sende done når vedtak ${eksisterendeVedtak.id} ikke ligger i  databasen")
    }

    private fun skapNokkel(
        varselId: String,
        fnr: String,
    ) = NokkelInputBuilder()
        .withEventId(varselId)
        .withGrupperingsId(varselId)
        .withFodselsnummer(fnr)
        .withNamespace("flex")
        .withAppnavn("spinnsyn-brukernotifikasjon")
        .build()

    private fun ZonedDateTime.erFornuftigTidspunktForVarsling(): Boolean {
        val osloTid = LocalDateTime.ofInstant(this.toInstant(), ZoneId.of("Europe/Oslo"))
        if (osloTid.dayOfWeek in listOf(SATURDAY, SUNDAY)) return false
        return osloTid.hour in 9..14 // 9:00 -> 14:59
    }
}
