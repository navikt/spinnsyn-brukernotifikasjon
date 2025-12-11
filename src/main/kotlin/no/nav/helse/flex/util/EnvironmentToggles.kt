package no.nav.helse.flex.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class EnvironmentToggles(
    @param:Value("\${nais.cluster}") private val naisCluster: String,
) {
    fun isProduction() = "prod-gcp" == naisCluster

    fun isNotProduction() = !isProduction()
}
