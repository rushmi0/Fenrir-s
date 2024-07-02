package org.fenrirs.relay.core.nip01

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.serialization.json.*

import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.relay.modules.TagElt

import org.slf4j.LoggerFactory

object Transform : VerificationFactory() {

    private fun Map<String, JsonElement>.toTagMap(): Map<TagElt, Set<String>> {
        return this.filterKeys { it.startsWith("#") }
            .mapKeys { (key, _) -> TagElt.valueOf(key.removePrefix("#")) }
            .mapValues { (_, value) ->
                value.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull }.toSet()
            }
    }

    private fun convertToFiltersXObject(field: Map<String, JsonElement>): FiltersX {
        return FiltersX(
            ids = field["ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            authors = field["authors"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet() ?: emptySet(),
            kinds = field["kinds"]?.jsonArray?.mapNotNull { it.jsonPrimitive.long }?.toSet() ?: emptySet(),
            tags = field.toTagMap(),
            since = field["since"]?.jsonPrimitive?.longOrNull,
            until = field["until"]?.jsonPrimitive?.longOrNull,
            limit = field["limit"]?.jsonPrimitive?.longOrNull,
            search = field["search"]?.jsonPrimitive?.contentOrNull
        )
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

    fun JsonObject.toFiltersX(): FiltersX = convertToFiltersXObject(this.toMap())

    fun JsonObject.toEvent(): Event = convertToEventObject(this.toMap())

    /*
    fun JsonObject.toFiltersX(): FiltersX {
        return Json.decodeFromJsonElement<FiltersX>(this)
    }

    fun JsonObject.toEvent(): Event {
        return Json.decodeFromJsonElement<Event>(this)
    }
     */


}
