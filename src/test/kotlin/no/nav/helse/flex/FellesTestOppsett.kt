package no.nav.helse.flex

import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VEDTAK_STATUS_TOPIC
import no.nav.helse.flex.kafka.VedtakStatusKafkaListener
import no.nav.helse.flex.kafka.nyttVarselTopic
import org.amshove.kluent.shouldBeEmpty
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.shaded.org.awaitility.Awaitility
import org.testcontainers.utility.DockerImageName
import java.time.Duration

private class PostgreSQLContainer14 : PostgreSQLContainer<PostgreSQLContainer14>("postgres:14-alpine")

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
abstract class FellesTestOppsett {
    companion object {
        init {
            PostgreSQLContainer14().also {
                it.start()
                System.setProperty("spring.datasource.url", it.jdbcUrl)
                System.setProperty("spring.datasource.username", it.username)
                System.setProperty("spring.datasource.password", it.password)
            }

            KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).also {
                it.start()
                System.setProperty("KAFKA_BROKERS", it.bootstrapServers)
            }
        }
    }

    @Autowired
    lateinit var vedtakStatusKafkaListener: VedtakStatusKafkaListener

    @Autowired
    lateinit var aivenKafkaProducer: KafkaProducer<String, String>

    @Autowired
    lateinit var varslingConsumer: Consumer<String, String>

    @AfterAll
    fun `Vi leser oppgave kafka topicet og feil hvis noe finnes og slik at subklassetestene leser alt`() {
        varslingConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    @BeforeAll
    fun `Vi leser oppgave og done kafka topicet og feiler om noe eksisterer`() {
        varslingConsumer.subscribeHvisIkkeSubscribed(nyttVarselTopic)

        varslingConsumer.hentProduserteRecords().shouldBeEmpty()
    }

    fun produserVedtakStatus(
        id: String,
        fnr: String,
        status: VedtakStatus,
    ) {
        val acks = vedtakStatusKafkaListener.meldingerAck

        aivenKafkaProducer.send(
            ProducerRecord(
                VEDTAK_STATUS_TOPIC,
                id,
                VedtakStatusDTO(
                    id = id,
                    fnr = fnr,
                    vedtakStatus = status,
                ).serialisertTilString(),
            ),
        )

        ventTilConsumerAck(1, acks)
    }

    fun ventTilConsumerAck(
        n: Int?,
        acks: Int = vedtakStatusKafkaListener.meldingerAck,
    ) {
        if (n == null) {
            Awaitility
                .await()
                .pollDelay(Duration.ofSeconds(2))
                .until { true }
        } else {
            val ferdig = acks + n

            Awaitility
                .await()
                .atMost(Duration.ofSeconds(5))
                .until {
                    vedtakStatusKafkaListener.meldingerAck == ferdig
                }
        }
    }

    fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)
}
