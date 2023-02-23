package no.nav.helse.flex.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.kafka.BrukernotifikasjonKafkaProdusent
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.shaded.org.awaitility.Awaitility
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.Instant

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class SendOppgaveTransaction {

    @MockBean
    private lateinit var brukernotifikasjonKafkaProdusent: BrukernotifikasjonKafkaProdusent

    @Autowired
    private lateinit var brukernotifikasjonService: BrukernotifikasjonService

    @Autowired
    private lateinit var brukernotifikasjonRepository: BrukernotifikasjonRepository

    companion object {
        private class PostgreSQLContainer12 : PostgreSQLContainer<PostgreSQLContainer12>("postgres:12-alpine")

        init {
            PostgreSQLContainer12().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.1.0")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }
        }
    }

    private val fnr = "111"

    @BeforeAll
    fun fyllDatabase() {
        val now = Instant.now()

        brukernotifikasjonRepository.insert(
            id = "9rehg93hr9g3h",
            fnr = fnr,
            ferdig = false,
            mottatt = now
        )
        brukernotifikasjonRepository.insert(
            id = "3ijrgij3rgj3g",
            fnr = fnr,
            ferdig = false,
            mottatt = now.plusSeconds(10)
        )
        brukernotifikasjonRepository.insert(
            id = "30rgj39rg93jrg9",
            fnr = fnr,
            ferdig = false,
            mottatt = now.plusSeconds(20)
        )
    }

    @Test
    fun `Ruller tilbake hvis kafka er nede`() {
        whenever(brukernotifikasjonKafkaProdusent.opprettBrukernotifikasjonOppgave(any(), any()))
            .thenThrow(RuntimeException("Kafka hikke"))

        assertThatThrownBy {
            brukernotifikasjonService.sendOppgave(
                brukerSineVedtak = brukernotifikasjonRepository.findBrukernotifikasjonDbRecordByFnr(fnr)
            )
        }.isInstanceOf(RuntimeException::class.java)

        Awaitility.await().atMost(Duration.ofSeconds(5)).until {
            brukernotifikasjonRepository.findBrukernotifikasjonDbRecordByFnr(fnr).all {
                it.varselId == null
            }
        }
    }
}
