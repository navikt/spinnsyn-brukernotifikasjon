package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BrukernotifikasjonJob(
    val leaderElection: LeaderElection,
    val brukernotifikasjonService: BrukernotifikasjonService
) {
    val log = logger()

    @Scheduled(
        initialDelay = 1000L * 60 * 2,
        fixedDelay = 1000L * 60 * 10,
    )
    fun run() {
        if (leaderElection.isLeader()) {
            brukernotifikasjonService.cronJob()
        } else {
            log.info("Kjører ikke cronjob siden denne podden ikke er leader")
        }
    }
}
