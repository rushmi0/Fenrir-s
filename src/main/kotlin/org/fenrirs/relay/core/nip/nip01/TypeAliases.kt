package org.fenrirs.relay.core.nip.nip01

import org.fenrirs.relay.core.nip.nip01.command.Command

typealias CommandParseResult = Pair<Command?, ValidationResult>
typealias EventCommandResult = Pair<Command, ValidationResult>
