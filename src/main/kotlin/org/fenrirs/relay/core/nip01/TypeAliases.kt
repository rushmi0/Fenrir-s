package org.fenrirs.relay.core.nip01

import org.fenrirs.relay.core.nip01.command.Command
import org.fenrirs.relay.policy.FiltersX

typealias ValidationResult = Pair<Boolean, String>
typealias CommandParseResult = Pair<Command?, ValidationResult>
typealias EventCommandResult = Pair<Command, ValidationResult>
typealias SubscriptionData = Map<String, List<FiltersX>>
