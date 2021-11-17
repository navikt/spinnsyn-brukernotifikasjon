package no.nav.helse.flex.kafka

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.VedtakStatusService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

const val VEDTAK_STATUS_TOPIC = "flex.vedtak-status"

@Component
class VedtakKafkaListener(
    private val vedtakStatusService: VedtakStatusService
) {

    val log = logger()

    @KafkaListener(
        topics = [VEDTAK_STATUS_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"], // TODO: Fjern
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        log.info("Mottok vedtak status for id: ${cr.key()}")

        vedtakStatusService.handterMelding(cr)

        acknowledgment.acknowledge()
    }
}
