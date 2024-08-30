package org.fenrirs.relay.core.nip01

import kotlinx.serialization.json.JsonElement
import org.fenrirs.relay.core.nip01.command.Command

// สร้างชื่อเรียกย่อ สำหรับโครงสร้างข้อมูลที่ซับซ้อน
typealias ValidationResult = Pair<Boolean, String>
typealias CommandParseResult = Pair<Command?, ValidationResult>
typealias EventCommandResult = Pair<Command, ValidationResult>
typealias FiltersData = Map<String, JsonElement>