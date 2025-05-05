package no.nav.helse.flex.service

import no.nav.helse.flex.db.BrukernotifikasjonDbRecord
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.kafka.nyttVarselTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.metrikk.Metrikk
import no.nav.tms.varsel.action.*
import no.nav.tms.varsel.builder.VarselActionBuilder
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
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
    private val kafkaProducer: KafkaProducer<String, String>,
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
                }.forEach { (_, brukerSineVedtak) ->
                    sendOppgave(brukerSineVedtak)
                    antall++
                }
        }

        return antall
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun sendOppgave(brukerSineVedtak: List<BrukernotifikasjonDbRecord>) {
        val varselID = UUID.randomUUID().toString()
        val fnr = brukerSineVedtak.first().fnr
        val sendtTidspunkt = LocalDateTime.now() // Brukernotifikasjon forventer at sendtTidspunkt settes til UTC

        brukerSineVedtak.forEach {
            brukernotifikasjonRepository.settVarselId(
                varselId = varselID,
                sendt = sendtTidspunkt.atZone(ZoneId.of("Europe/Oslo")).toInstant(),
                id = it.id,
            )
        }

        metrikk.brukernotifikasjonSendt(brukerSineVedtak.size)

        val opprettVarsel =
            VarselActionBuilder.opprett {
                type = Varseltype.Oppgave
                varselId = varselID
                sensitivitet = Sensitivitet.High
                ident = fnr
                tekst =
                    Tekst(
                        spraakkode = "nb",
                        tekst = "Du har fått svar på søknaden om sykepenger - se vedtaket",
                        default = true,
                    )
                aktivFremTil = null
                link = spinnsynFrontendUrl
                eksternVarsling =

                    EksternVarslingBestilling(
                        prefererteKanaler = listOf(EksternKanal.SMS),
                        smsVarslingstekst = "Hei! Du har fått et vedtak fra Nav. Logg inn på Nav for å se svaret. Vennlig hilsen Nav",
                    )
            }

        kafkaProducer.send(ProducerRecord(nyttVarselTopic, varselID, opprettVarsel)).get()

        log.info("Sendte brukernotifikasjon med varsel id $varselID for vedtak ${brukerSineVedtak.map { it.id }}")
    }

    @Transactional(rollbackFor = [Throwable::class])
    fun sendDone(eksisterendeVedtak: BrukernotifikasjonDbRecord) {
        val now = LocalDateTime.now()

        brukernotifikasjonRepository
            .findByIdOrNull(eksisterendeVedtak.id)
            ?.let {
                val varselID = it.varselId!!

                brukernotifikasjonRepository.settTilFerdigMedVarselId(
                    varselId = varselID,
                    sendt = now.atZone(ZoneId.of("Europe/Oslo")).toInstant(),
                )

                metrikk.brukernotifikasjonDone()

                val inaktiverVarsel =
                    VarselActionBuilder.inaktiver {
                        varselId = varselID
                    }

                kafkaProducer.send(ProducerRecord(nyttVarselTopic, varselID, inaktiverVarsel)).get()
            }
            ?: throw RuntimeException("Kan ikke sende done når vedtak ${eksisterendeVedtak.id} ikke ligger i  databasen")
    }

    private fun ZonedDateTime.erFornuftigTidspunktForVarsling(): Boolean {
        val osloTid = LocalDateTime.ofInstant(this.toInstant(), ZoneId.of("Europe/Oslo"))
        if (osloTid.dayOfWeek in listOf(SATURDAY, SUNDAY)) return false
        return osloTid.hour in 9..14 // 9:00 -> 14:59
    }
}
