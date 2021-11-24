package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@Component
class BrukernotifikasjonJob(
    val leaderElection: LeaderElection,
    val brukernotifikasjonService: BrukernotifikasjonService
) {
    private val log = logger()
    private val osloZone: ZoneId = ZoneId.of("Europe/Oslo")
    private val tidspunkt: ZonedDateTime = LocalDateTime.of(2021, 11, 24, 12, 0).atZone(osloZone)

    @Scheduled(
        initialDelay = 1000L * 60 * 2,
        fixedDelay = 1000L * 60 * 10,
    )
    fun run() {
        if (leaderElection.isLeader()) {
            brukernotifikasjonService.cronJob()

            log.info("Tidspunktet er: $tidspunkt")
        } else {
            log.info("Kjører ikke cronjob siden denne podden ikke er leader")
        }
    }
}
