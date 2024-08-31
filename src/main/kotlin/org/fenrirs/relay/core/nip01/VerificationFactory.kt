package org.fenrirs.relay.core.nip01

import kotlinx.serialization.json.*
import org.fenrirs.relay.policy.TagElt

import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.FiltersXValidateField
import org.fenrirs.relay.policy.NostrField
import org.fenrirs.relay.policy.Event

import org.fenrirs.relay.core.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip01.Transform.toFiltersX
import org.fenrirs.relay.core.nip01.VerifyEvent.isEventPublicKeyValid
import org.fenrirs.relay.core.nip01.VerifyEvent.isValidEventId
import org.fenrirs.relay.core.nip01.VerifyEvent.isValidSignature
import org.fenrirs.relay.core.nip01.VerifyFilterX.validate

import org.slf4j.LoggerFactory


open class VerificationFactory {

    fun validateElement(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult {
        val (isFieldNamesValid, fieldNamesError) = checkFieldNames(receive, relayPolicy)
        val (isDataValid, dataError) = validateDataType(receive, relayPolicy)

        return when {
            !isFieldNamesValid -> false to fieldNamesError
            !isDataValid -> false to dataError
            else -> true to ""
        }
    }

    private fun checkFieldNames(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult {
        val allowedFields = relayPolicy.map { it.fieldName }.toSet()
        val invalidFields: List<String> = receive.keys.filter { it !in allowedFields && !isValidTag(it) }

        val warning = if (invalidFields.isNotEmpty()) buildErrorMessage(invalidFields) else ""
        return if (warning.isEmpty()) true to "" else false to warning
    }

    private fun isValidTag(tag: String): Boolean {
        if (!tag.startsWith("#") || tag.length < 2) {
            return false
        }
        val tagKey = tag.substring(1)
        return try {
            TagElt.valueOf(tagKey); true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun buildErrorMessage(invalidFields: List<String>): String =
        "unsupported: [${invalidFields.joinToString(", ")}] fields"

    private fun validateDataType(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult {

        receive.forEach { (fieldName, fieldValue) ->
            val expectedType = relayPolicy.find { policy -> policy.fieldName == fieldName }?.fieldType
            val actualType = inspectDataType(fieldValue)

            if (expectedType != actualType) {
                val warning = "invalid: data type at [$fieldName] field"
                LOG.error(warning)
                return false to warning
            }
        }

        if (relayPolicy.isArrayOfPolicy<EventValidateField>()) {
            val missingFields = relayPolicy.filterNot { field -> receive.containsKey(field.fieldName) }
            if (missingFields.isNotEmpty()) {
                val missingFieldNames = missingFields.joinToString(", ") { field -> field.fieldName }
                val warning = "invalid: missing fields: [$missingFieldNames]"
                LOG.error(warning)
                return false to warning
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

    private fun inspectValue(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): ValidationResult =  when {
        relayPolicy.isArrayOfPolicy<FiltersXValidateField>() -> validateFiltersX(receive)
        relayPolicy.isArrayOfPolicy<EventValidateField>() -> validateEvent(receive)
        else -> false to "unsupported: relay policy"
    }

    private fun validateFiltersX(receive: Map<String, JsonElement>): ValidationResult {
        val filter = receive.toFiltersX()

        return filter.validate()
    }


    private fun validateEvent(receive: Map<String, JsonElement>): ValidationResult {
        val event: Event = receive.toEvent()

        val (isValidId, eventIdWarning) = event.isValidEventId()
        if (!isValidId) {
            LOG.error(eventIdWarning)
            return false to eventIdWarning
        }

        val (isValidSignature, signatureWarning) = event.isValidSignature()
        if (!isValidSignature) {
            LOG.error(signatureWarning)
            return false to signatureWarning
        }

        val (isValidPubkey, pubkeyWarning) = event.isEventPublicKeyValid()
        if (!isValidPubkey) {
            LOG.error(pubkeyWarning)
            return false to pubkeyWarning
        } else {
            true to pubkeyWarning
        }

        return true to ""
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(VerificationFactory::class.java)
        private inline fun <reified T> Array<*>.isArrayOfPolicy(): Boolean = all { it is T }
    }

}

