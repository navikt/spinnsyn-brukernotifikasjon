package no.nav.helse.flex.metrikk

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.springframework.stereotype.Component

@Component
class Metrikk(private val registry: MeterRegistry) {

    fun BRUKERNOTIFIKASJON_SENDT(forAntallVedtak: Int) =
        registry.counter(
            "brukernotifkasjon_sendt_counter",
            Tags.of("antall", "$forAntallVedtak")
        ).increment()

    fun BRUKERNOTIFIKASJON_DONE() = registry.counter(
        "brukernotifkasjon_done_counter"
    ).increment()
}
