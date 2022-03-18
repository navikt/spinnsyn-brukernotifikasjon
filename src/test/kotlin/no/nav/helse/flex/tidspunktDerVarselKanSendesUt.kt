package no.nav.helse.flex

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

fun tidspunktDerVarselKanSendesUt(): ZonedDateTime {
    fun DayOfWeek.erHelg(): Boolean {
        return this == DayOfWeek.SATURDAY || this == DayOfWeek.SUNDAY
    }
    val tidspunkt = Instant.now()
        .atZone(ZoneId.of("Europe/Oslo"))
        .plusDays(1)
        .withHour(12)
    if (tidspunkt.dayOfWeek.erHelg()) {
        return tidspunkt.plusDays(2)
    }
    return tidspunkt
}
