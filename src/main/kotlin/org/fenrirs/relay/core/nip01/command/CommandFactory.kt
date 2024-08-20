package org.fenrirs.relay.core.nip01.command

import kotlinx.serialization.json.*

import org.fenrirs.relay.policy.Event
import org.fenrirs.relay.policy.FiltersX

import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.FiltersXValidateField

import org.fenrirs.relay.core.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip01.Transform.toFiltersX
import org.fenrirs.relay.core.nip01.Transform.validateElement
import org.fenrirs.relay.policy.NostrRelayConfig
import org.fenrirs.stored.Environment

import org.slf4j.LoggerFactory

/**
 * CommandFactory เป็นอ็อบเจกต์ที่ใช้ในการประมวลผลคำสั่งที่ส่งมาจากไคลเอนต์
 */
object CommandFactory {

    private val env: Environment by lazy {
        Environment(NostrRelayConfig())
    }

    /**
     * parse ใช้ในการแยกและวิเคราะห์คำสั่งที่ส่งมาจากไคลเอนต์
     * @param payload ข้อมูล JSON ที่เป็นคำสั่ง
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน (เช่น สถานะการประมวลผลเป็น true หมายถึงสำเร็จ และข้อความเตือนว่าเกิดข้อผิดพลาด)
     */
    fun parse(payload: String): Pair<Command?, Pair<Boolean, String>> {
        val jsonElement = try {
            Json.parseToJsonElement(payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("invalid: JSON format")
        }

        if (jsonElement !is JsonArray || jsonElement.isEmpty()) {
            throw IllegalArgumentException("invalid: command format")
        }

        return when (val cmd = jsonElement[0].jsonPrimitive.content) {
            "EVENT" -> parseEvent(jsonElement)
            "REQ" -> parseREQ(jsonElement)
            "CLOSE" -> parseClose(jsonElement)
            // "COUNT" -> TODO("Not yet implemented")
            // "AUTH" -> TODO("Not yet implemented")
            else -> throw IllegalArgumentException("Unknown command: $cmd")
        }
    }


    /**
     * eventCommand ใช้ในการแยกและวิเคราะห์คำสั่งประเภท EVENT
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท EVENT
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน
     */
    private fun parseEvent(jsonArray: JsonArray): Pair<Command, Pair<Boolean, String>> {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonObject) {
            throw IllegalArgumentException("invalid: EVENT command format")
        }
        val eventJson = jsonArray[1].jsonObject
        val event: Event = eventJson.toEvent()
        val data: Map<String, JsonElement> = eventJson.toMap()

        val (status, warning) = validateElement(data, EventValidateField.entries.toTypedArray())
        return EVENT(event) to (status to warning)
    }


    /**
     * reqCommand ใช้ในการแยกและวิเคราะห์คำสั่งประเภท REQ
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท REQ
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน
     */
    private fun parseREQ(jsonArray: JsonArray): Pair<Command, Pair<Boolean, String>> {
        if (jsonArray.size < 3 || jsonArray[1] !is JsonPrimitive || jsonArray.drop(2).any { it !is JsonObject }) {
            throw IllegalArgumentException("invalid: REQ command format")
        }
        val subscriptionId: String = jsonArray[1].jsonPrimitive.content
        val filtersJson: List<JsonObject> = jsonArray.drop(2).map { it.jsonObject }
        //LOG.info("filters object ${filtersJson.size}: $filtersJson")

        if (filtersJson.size > env.MAX_FILTERS) {
            throw IllegalArgumentException("rate-limited: max filters ${env.MAX_FILTERS} values in each sub ID allowed")
        }

        val data: Map<String, JsonElement> = filtersJson.flatMap { it.entries }.associate { it.key to it.value }

        val filtersX: List<FiltersX> = filtersJson.map { it.jsonObject.toFiltersX() }

        val (status, warning) = validateElement(data, FiltersXValidateField.entries.toTypedArray())
        return REQ(subscriptionId, filtersX) to (status to warning)
    }


    /**
     * closeCommand ใช้ในการแยกและวิเคราะห์คำสั่งประเภท CLOSE
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท CLOSE
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน
     */
    private fun parseClose(jsonArray: JsonArray): Pair<Command, Pair<Boolean, String>> {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonPrimitive) {
            throw IllegalArgumentException("invalid: CLOSE command format")
        }
        val subscriptionId = jsonArray[1].jsonPrimitive.content
        return CLOSE(subscriptionId) to (true to "")
    }

    private val LOG = LoggerFactory.getLogger(CommandFactory::class.java)


}