package org.fenrirs.relay.core.nip.nip01

import kotlinx.serialization.json.*

import org.fenrirs.relay.models.EventValidateField
import org.fenrirs.relay.models.FiltersXValidateField
import org.fenrirs.relay.models.NostrField

import org.fenrirs.relay.core.nip.nip01.Transform.toFiltersX
import org.fenrirs.relay.core.nip.nip01.VerifyFilterX.validate

/**
 * ตรวจสอบโครงสร้างของ JSON ที่ได้รับ (ชื่อ field ที่อนุญาต, ชนิดข้อมูล, field ที่จำเป็น) สำหรับทั้ง Event และ FiltersX
 * การตรวจสอบเชิงความหมายตาม NIP-01 ของ Event (event id / signature / pubkey) ไม่ได้อยู่ในคลาสนี้อีกต่อไป
 * แต่ย้ายไปเป็น [VerifyEvent.verifyNip01] ที่ทำงานกับ Event object ที่ถูกสร้างเพียงครั้งเดียวใน CommandFactory
 * เพื่อไม่ต้องแปลง JSON เป็น Event object ซ้ำสองรอบเหมือนโค้ดเดิม
 */
open class VerificationFactory {

    fun validateElement(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult {
        val fieldNameCheck = checkFieldNames(receive, relayPolicy)
        if (!fieldNameCheck.isValid) return fieldNameCheck

        return validateDataType(receive, relayPolicy)
    }

    private fun checkFieldNames(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult {
        val allowedFields = relayPolicy.map { it.fieldName }.toSet()
        val allowsDynamicTags = relayPolicy.isArrayOfPolicy<FiltersXValidateField>()

        val invalidFields: List<String> = receive.keys.filter { fieldName ->
            fieldName !in allowedFields && !(allowsDynamicTags && fieldName.startsWith("#"))
        }

        return if (invalidFields.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.invalid(buildErrorMessage(invalidFields))
        }
    }

    private fun buildErrorMessage(invalidFields: List<String>): String =
        "unsupported: [${invalidFields.joinToString(", ")}] fields"

    private fun validateDataType(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult {

        val allowsDynamicTags = relayPolicy.isArrayOfPolicy<FiltersXValidateField>()

        receive.forEach { (fieldName, fieldValue) ->
            // field "#<tag>" แบบไดนามิก (ตาม NIP-12) คาดหวังว่าเป็น array เสมอ ไม่ต้องมี enum entry ตายตัว
            val expectedType = relayPolicy.find { policy -> policy.fieldName == fieldName }?.fieldType
                ?: (ArrayList::class.java.takeIf { allowsDynamicTags && fieldName.startsWith("#") })
            val actualType = inspectDataType(fieldValue)

            if (expectedType != actualType) {
                return ValidationResult.invalid("invalid: data type at [$fieldName] field")
            }
        }

        if (relayPolicy.isArrayOfPolicy<EventValidateField>()) {
            val missingFields = relayPolicy.filterNot { field -> receive.containsKey(field.fieldName) }
            if (missingFields.isNotEmpty()) {
                val missingFieldNames = missingFields.joinToString(", ") { field -> field.fieldName }
                return ValidationResult.invalid("invalid: missing fields: [$missingFieldNames]")
            }
        }

        return inspectValue(receive, relayPolicy)
    }

    private fun inspectDataType(receive: JsonElement): Class<*> {
        return when (receive) {
            is JsonPrimitive -> determinePrimitiveType(receive)
            is JsonArray -> ArrayList::class.java
            is JsonObject -> Map::class.java
            else -> receive.toString()::class.java
        }
    }

    private fun determinePrimitiveType(receive: JsonPrimitive): Class<*> = when {
        receive.isString -> String::class.java
        receive.booleanOrNull != null -> Boolean::class.java
        receive.longOrNull != null -> Long::class.java
        receive.doubleOrNull != null -> Double::class.java
        else -> Any::class.java
    }

    /**
     * สำหรับ FiltersX จะตรวจสอบเชิงความหมาย (ids/authors/since-until/limit) ทันที เพราะเป็นการตรวจสอบข้อมูล
     * ที่รวมมาจากหลาย filter object (aggregate view) ซึ่งต่างจาก object จริงที่ parseSubWithFilters สร้างขึ้น
     * ส่วน Event จะคืนแค่ Valid เพราะการตรวจสอบเชิงความหมายทำที่ CommandFactory โดยตรงกับ Event ที่สร้างไว้แล้ว
     */
    private fun inspectValue(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult = when {
        relayPolicy.isArrayOfPolicy<FiltersXValidateField>() -> validateFiltersX(receive)
        relayPolicy.isArrayOfPolicy<EventValidateField>() -> ValidationResult.Valid
        else -> ValidationResult.invalid("unsupported: relay policy")
    }

    private fun validateFiltersX(receive: Map<String, JsonElement>): ValidationResult {
        return receive.toFiltersX().validate()
    }

    companion object {
        private inline fun <reified T> Array<*>.isArrayOfPolicy(): Boolean = all { it is T }
    }

}