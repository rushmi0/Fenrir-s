package org.fenrirs.relay.service.nip01.command

import kotlinx.serialization.Serializable
import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX

@Serializable
sealed class Command

@Serializable
data class EVENT(val event: Event) : Command()

@Serializable
data class REQ(val subscriptionId: String, val filtersX: List<FiltersX>) : Command()

@Serializable
data class CLOSE(val subscriptionId: String) : Command()

@Serializable
data class AUTH(val challenge: String) : Command()