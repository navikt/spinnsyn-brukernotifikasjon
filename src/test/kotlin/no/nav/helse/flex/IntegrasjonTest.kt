package no.nav.helse.flex

import no.nav.helse.flex.domene.VedtakStatus
import no.nav.helse.flex.domene.VedtakStatusDTO
import no.nav.helse.flex.kafka.VEDTAK_STATUS_TOPIC
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class IntegrasjonTest : AbstractContainerBaseTest() {

    @Autowired
    lateinit var aivenKafkaProducer: KafkaProducer<String, String>

    final val id = "9834-fsdd-12"
    final val fnr = "58229418431"
    final val vedtakStatus = VedtakStatusDTO(
        id = id,
        fnr = fnr,
        vedtakStatus = VedtakStatus.MOTATT
    )

    @Test
    fun `Vedtak status legges på kafka`() {
        aivenKafkaProducer.send(
            ProducerRecord(
                VEDTAK_STATUS_TOPIC,
                id,
                vedtakStatus.serialisertTilString()
            )
        )
    }
}
