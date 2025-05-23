package no.nav.helse.flex.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.tidspunktVarselKanSendesUt
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.Duration
import java.time.Instant

class SendDoneTransaction : FellesTestOppsett() {
    @MockitoBean
    lateinit var varslingProducer: KafkaProducer<String, String>

    @Autowired
    private lateinit var brukernotifikasjonService: BrukernotifikasjonService

    @Autowired
    private lateinit var brukernotifikasjonRepository: BrukernotifikasjonRepository

    private val fnr = "22222222222"

    @BeforeAll
    fun fyllDatabase() {
        val now = Instant.now()

        brukernotifikasjonRepository.insert(
            id = "43jfe0ioewnf",
            fnr = fnr,
            ferdig = false,
            mottatt = now,
        )
        brukernotifikasjonRepository.insert(
            id = "f2i42mfp2fem",
            fnr = fnr,
            ferdig = false,
            mottatt = now.plusSeconds(10),
        )
        brukernotifikasjonRepository.insert(
            id = "p32o4kgp3kr",
            fnr = fnr,
            ferdig = false,
            mottatt = now.plusSeconds(20),
        )
        whenever(varslingProducer.send(any())).thenReturn(mock())

        brukernotifikasjonService
            .cronJob(
                tidspunktVarselKanSendesUt(),
            ).shouldBeEqualTo(1)
    }

    @Test
    fun `Ruller tilbake hvis done melding ikke kan legges på kafka`() {
        whenever(varslingProducer.send(any(), any()))
            .thenThrow(RuntimeException("Kafka hikke"))

        assertThatThrownBy {
            brukernotifikasjonService.sendDone(
                eksisterendeVedtak = brukernotifikasjonRepository.findBrukernotifikasjonDbRecordByFnr(fnr).first(),
            )
        }.isInstanceOf(RuntimeException::class.java)

        Awaitility.await().atMost(Duration.ofSeconds(5)).until {
            brukernotifikasjonRepository.findBrukernotifikasjonDbRecordByFnr(fnr).all {
                it.doneSendt == null
            }
        }
    }
}
