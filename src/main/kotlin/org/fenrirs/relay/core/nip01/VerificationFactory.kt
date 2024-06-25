package org.fenrirs.relay.core.nip01

import jakarta.inject.Singleton

import kotlinx.serialization.json.*
import org.fenrirs.relay.modules.TagElt

import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.FiltersXValidateField
import org.fenrirs.relay.policy.NostrField

import org.fenrirs.relay.core.nip01.Transform.convertToEventObject
import org.fenrirs.relay.core.nip01.Transform.convertToFiltersXObject
import org.fenrirs.relay.core.nip01.VerifyEvent.isValidEventId
import org.fenrirs.relay.core.nip01.VerifyEvent.isValidSignature

import org.slf4j.LoggerFactory

@Singleton
open class VerificationFactory {

    fun validateElement(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): Pair<Boolean, String> {
        val (isFieldNamesValid, fieldNamesError) = checkFieldNames(receive, relayPolicy)
        val (isDataValid, dataError) = validateDataType(receive, relayPolicy)

        return when {
            !isFieldNamesValid -> Pair(false, fieldNamesError)
            !isDataValid -> Pair(false, dataError)
            else -> Pair(true, "")
        }
    }

    private fun checkFieldNames(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): Pair<Boolean, String> {
        val allowedFields = relayPolicy.map { it.fieldName }.toSet()
        val invalidFields: List<String> = receive.keys.filter { it !in allowedFields && !isValidTag(it) }

        val warning = if (invalidFields.isNotEmpty()) buildErrorMessage(invalidFields) else ""

        return if (warning.isEmpty()) Pair(true, "") else Pair(false, warning)
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

    private fun buildErrorMessage(invalidFields: List<String>): String {
        return "unsupported: [${invalidFields.joinToString(", ")}] fields"
    }

    fun validateDataType(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): Pair<Boolean, String> {

        receive.forEach { (fieldName, fieldValue) ->
            val expectedType = relayPolicy.find { policy -> policy.fieldName == fieldName }?.fieldType
            val actualType = inspectDataType(fieldValue)

            if (expectedType != actualType) {
                val warning = "invalid: data type at [$fieldName] field"
                LOG.info(warning)
                return Pair(false, warning)
            }
        }

        if (relayPolicy.isArrayOfPolicy<EventValidateField>()) {
            val missingFields = relayPolicy.filterNot { field -> receive.containsKey(field.fieldName) }
            if (missingFields.isNotEmpty()) {
                val missingFieldNames = missingFields.joinToString(", ") { field -> field.fieldName }
                val warning = "invalid: missing fields: [$missingFieldNames]"
                LOG.info(warning)
                return Pair(false, warning)
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

    private fun determinePrimitiveType(receive: JsonPrimitive): Class<*> {
        return when {
            receive.isString -> String::class.java
            receive.booleanOrNull != null -> Boolean::class.java
            receive.longOrNull != null -> Long::class.java
            receive.doubleOrNull != null -> Double::class.java
            else -> Any::class.java
        }
    }

    private fun inspectValue(
        receive: Map<String, JsonElement>,
        relayPolicy: Array<out NostrField>
    ): Pair<Boolean, String> {
        return when {
            relayPolicy.isArrayOfPolicy<FiltersXValidateField>() -> validateFiltersX(receive)
            relayPolicy.isArrayOfPolicy<EventValidateField>() -> validateEvent(receive)
            else -> Pair(false, "unsupported: relay policy")
        }
    }

    private fun validateFiltersX(receive: Map<String, JsonElement>): Pair<Boolean, String> {
        val filter = convertToFiltersXObject(receive)

        return Pair(true, "Not yet implemented")
    }

    private fun validateEvent(receive: Map<String, JsonElement>): Pair<Boolean, String> {
        val event = convertToEventObject(receive)

        val (isValidId, actualId) = event.isValidEventId()
        if (!isValidId) {
            val warning = "invalid: actual event id $actualId"
            LOG.info(warning)
            return Pair(false, warning)
        }

        val (isValidSignature, signatureWarning) = event.isValidSignature()
        if (!isValidSignature) {
            LOG.info(signatureWarning)
            return Pair(false, signatureWarning)
        }

        return Pair(true, "")
    }

    private inline fun <reified T> Array<*>.isArrayOfPolicy(): Boolean = all { it is T }

    private val LOG = LoggerFactory.getLogger(VerificationFactory::class.java)

}

