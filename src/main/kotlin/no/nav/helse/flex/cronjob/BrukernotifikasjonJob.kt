package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class BrukernotifikasjonJob(
    val leaderElection: LeaderElection,
    val brukernotifikasjonService: BrukernotifikasjonService,
) {
    private val log = logger()

    @Scheduled(
        initialDelay = 2,
        fixedDelay = 10,
        timeUnit = TimeUnit.MINUTES,
    )
    fun run() {
        if (leaderElection.isLeader()) {
            brukernotifikasjonService.cronJob()
        } else {
            log.info("Kjører ikke cronjob siden denne podden ikke er leder.")
        }
    }
}
