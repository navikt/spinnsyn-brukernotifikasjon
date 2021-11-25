package no.nav.helse.flex.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.kafka.BrukernotifikasjonKafkaProdusent
import org.amshove.kluent.shouldBeEqualTo
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
import java.time.ZoneId

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
class SendDoneTransaction {

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
                System.setProperty("on-prem-kafka.bootstrap-servers", it.bootstrapServers)
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }
        }
    }

    private val fnr = "222"

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

        brukernotifikasjonService
            .cronJob(
                now.atZone(ZoneId.of("Europe/Oslo")).plusDays(1).withHour(12)
            )
            .shouldBeEqualTo(1)
    }

    @Test
    fun `Ruller tilbake hvis done melding ikke kan legges på kafka`() {
        whenever(brukernotifikasjonKafkaProdusent.sendDonemelding(any(), any()))
            .thenThrow(RuntimeException("Kafka hikke"))

        assertThatThrownBy {
            brukernotifikasjonService.sendDone(
                eksisterendeVedtak = brukernotifikasjonRepository.findBrukernotifikasjonDbRecordByFnr(fnr).first()
            )
        }.isInstanceOf(RuntimeException::class.java)

        Awaitility.await().atMost(Duration.ofSeconds(5)).until {
            brukernotifikasjonRepository.findBrukernotifikasjonDbRecordByFnr(fnr).all {
                it.doneSendt == null
            }
        }
    }
}
