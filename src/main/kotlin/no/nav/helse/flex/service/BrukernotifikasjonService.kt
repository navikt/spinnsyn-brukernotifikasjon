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
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Service
class BrukernotifikasjonService(
    private val brukernotifikasjonRepository: BrukernotifikasjonRepository,
    private val brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
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
                    log.info("Ville sendt 1 brukernotifikasjon for ${brukerSineVedtak.size} stk vedtak")
                    // TODO: Send brukernot
                    // TODO: Lagre kobling mellom varsel id og alle vedtak som var med i varsling
                    antall++
                }
        }

        return antall
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

        // TODO: Finne varsel id for dette vedtaket

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

    private fun ZonedDateTime.erFornuftigTidspunktForVarsling(): Boolean {
        // TODO: Remove
        val osloTid = LocalDateTime.ofInstant(this.toInstant(), ZoneId.of("Europe/Oslo"))
        log.info("UTC-tid = $this, osloTid = $osloTid")
        if (osloTid.dayOfWeek in listOf(SATURDAY, SUNDAY)) return false
        return osloTid.hour in 9..14 // 9:00 -> 14:59
    }
}
