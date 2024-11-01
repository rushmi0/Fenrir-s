package org.fenrirs.relay.core.nip01

import org.fenrirs.relay.core.nip01.command.Command
import org.fenrirs.relay.policy.FiltersX

// สร้างชื่อเรียกย่อ สำหรับโครงสร้างข้อมูลที่ซับซ้อน
typealias ValidationResult = Pair<Boolean, String>
typealias CommandParseResult = Pair<Command?, ValidationResult>
typealias EventCommandResult = Pair<Command, ValidationResult>
typealias SubscriptionData = Map<String, List<FiltersX>>
