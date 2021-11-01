package no.nav.helse.flex.domene

import org.springframework.data.annotation.Id
import java.time.Instant

data class Brukernotifikasjon(
    @Id
    val id: String,
    val fnr: String,
    val oppgaveSendt: Instant,
    val doneSendt: Instant?
)
