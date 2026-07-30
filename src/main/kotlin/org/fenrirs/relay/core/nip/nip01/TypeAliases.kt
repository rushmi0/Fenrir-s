package org.fenrirs.relay.core.nip.nip01

import org.fenrirs.relay.core.nip.nip01.command.Command
import org.fenrirs.relay.models.FiltersX

typealias CommandParseResult = Pair<Command?, ValidationResult>
typealias EventCommandResult = Pair<Command, ValidationResult>
typealias SubscriptionData = Map<String, List<FiltersX>>
