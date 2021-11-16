package no.nav.helse.flex.service

import no.nav.helse.flex.domene.tilVedtakStatus
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.stereotype.Service

@Service
class VedtakStatusService {

    fun handterMelding(cr: ConsumerRecord<String, String>) {
        cr.value().tilVedtakStatus()
    }
}
