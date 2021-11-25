package no.nav.helse.flex.service

import no.nav.brukernotifikasjon.schemas.Done
import no.nav.brukernotifikasjon.schemas.Nokkel
import no.nav.brukernotifikasjon.schemas.Oppgave
import no.nav.helse.flex.db.BrukernotifikasjonDbRecord
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.kafka.BrukernotifikasjonKafkaProdusent
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
        val sendtTidspunkt = Instant.now() // Brukernotifikasjon forventer at sendtTidspunkt settes til UTC, Instant.now() bruker UTC

        brukerSineVedtak.forEach {
            brukernotifikasjonRepository.settVarselId(
                varselId = varselId,
                sendt = sendtTidspunkt,
                id = it.id,
            )
        }

        brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(
            Nokkel(serviceuserUsername, varselId),
            Oppgave(
                sendtTidspunkt.toEpochMilli(),
                fnr,
                varselId,
                "Du har fått svar på søknaden om sykepenger - se resultatet",
                spinnsynFrontendUrl,
                4,
                true,
                listOf("SMS", "EPOST")
            )
        )

        log.info("Sendte brukernotifikasjon med varsel id $varselId for vedtak ${brukerSineVedtak.map { it.id }}")
    }

    @Transactional
    fun sendDone(eksisterendeVedtak: BrukernotifikasjonDbRecord) {
        val now = Instant.now()

        brukernotifikasjonRepository
            .findByIdOrNull(eksisterendeVedtak.id)
            ?.let {
                val varselId = it.varselId!!

                brukernotifikasjonRepository.settTilFerdigMedVarselId(
                    varselId = varselId,
                    sendt = now
                )

                brukernotifikasjonKafkaProdusent.sendDonemelding(
                    Nokkel(serviceuserUsername, varselId),
                    Done(
                        now.toEpochMilli(),
                        eksisterendeVedtak.fnr,
                        varselId,
                    )
                )
            }
            ?: throw RuntimeException("Kan ikke sende done når vedtak ${eksisterendeVedtak.id} ikke ligger i  databasen")
    }

    private fun ZonedDateTime.erFornuftigTidspunktForVarsling(): Boolean {
        val osloTid = LocalDateTime.ofInstant(this.toInstant(), ZoneId.of("Europe/Oslo"))
        if (osloTid.dayOfWeek in listOf(SATURDAY, SUNDAY)) return false
        return osloTid.hour in 9..14 // 9:00 -> 14:59
    }
}
