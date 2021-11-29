package no.nav.helse.flex.cronjob

import no.nav.helse.flex.db.BrukernotifikasjonRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.BrukernotifikasjonService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// TODO: Koden kan slettes etter jobben har kjørt ferdig etter "tidForSisteVarsel".
@Component
class BrukernotifikasjonJob(
    val leaderElection: LeaderElection,
    val brukernotifikasjonService: BrukernotifikasjonService,
    val brukernotifikasjonRepository: BrukernotifikasjonRepository,
) {
    private val log = logger()
    val tidForSisteVarsel: ZonedDateTime = LocalDateTime.of(2021, 11, 30, 0, 1).atZone(ZoneId.of(ZONE_ID_OSLO))

    @Scheduled(
        initialDelay = 1000L * 60 * 2,
        fixedDelay = 1000L * 60 * 10,
    )
    fun run() {
        if (leaderElection.isLeader()) {
            brukernotifikasjonService.cronJob()
            settTilFerdig()
        } else {
            log.info("Kjører ikke cronjob siden denne podden ikke er leder.")
        }
    }

    private fun settTilFerdig() {
        val antall = brukernotifikasjonRepository.settTilFerdigFørTidspunkt(tidForSisteVarsel.toInstant())
        log.info("Satt $antall vedtak til ferdig. Brukte [${tidForSisteVarsel.logformatert()}] som cutoff-tidspunkt.")
    }

    fun ZonedDateTime.logformatert(): String {
        return format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"))
    }
}

const val ZONE_ID_OSLO = "Europe/Oslo"
