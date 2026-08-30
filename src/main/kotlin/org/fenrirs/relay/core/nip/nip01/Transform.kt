package org.fenrirs.relay.core.nip.nip01

import kotlinx.serialization.json.*

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX


object Transform : VerificationFactory() {

    private fun Map<String, JsonElement>.toTagMap(): Map<String, Set<String>> {
        val result = LinkedHashMap<String, Set<String>>()
        for ((key, value) in this) {
            if (!key.startsWith("#")) continue
            val values = value.jsonArray.mapNotNullTo(LinkedHashSet()) { it.jsonPrimitive.contentOrNull }
            result[key.removePrefix("#")] = values
        }
        return result
    }

    private fun convertToFiltersXObject(field: Map<String, JsonElement>): FiltersX {
        return FiltersX {
            ids = field["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet()
            authors = field["authors"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet()
            kinds = field["kinds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.long }?.toSet() ?: emptySet()
            tags = field.toTagMap()
            since = field["since"]?.jsonPrimitive?.longOrNull
            until = field["until"]?.jsonPrimitive?.longOrNull
            limit = field["limit"]?.jsonPrimitive?.longOrNull
            search = field["search"]?.jsonPrimitive?.contentOrNull
        }
    }


    private fun convertToEventObject(field: Map<String, JsonElement>): Event {
        return Event(
            id = field["id"]?.jsonPrimitive?.contentOrNull,
            pubkey = field["pubkey"]?.jsonPrimitive?.contentOrNull,
            created_at = field["created_at"]?.jsonPrimitive?.longOrNull,
            kind = field["kind"]?.jsonPrimitive?.longOrNull,
            tags = field["tags"]?.jsonArray?.map { it.jsonArray.map { tag -> tag.jsonPrimitive.content } },
            content = field["content"]?.jsonPrimitive?.contentOrNull,
            sig = field["sig"]?.jsonPrimitive?.contentOrNull
        )
    }

    fun Map<String, JsonElement>.toFiltersX(): FiltersX = convertToFiltersXObject(this)

    fun Map<String, JsonElement>.toEvent(): Event = convertToEventObject(this)

    fun JsonObject.toFiltersX(): FiltersX = convertToFiltersXObject(this)

    fun JsonObject.toEvent(): Event = convertToEventObject(this)

    /*
    fun JsonObject.toFiltersX(): FiltersX {
        return Json.decodeFromJsonElement<FiltersX>(this)
    }

    fun JsonObject.toEvent(): Event {
        return Json.decodeFromJsonElement<Event>(this)
    }
     */


}