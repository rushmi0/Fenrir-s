package org.fenrirs.relay.service.nip01.command

import kotlinx.serialization.json.*
import org.fenrirs.relay.modules.Event
import org.fenrirs.relay.modules.FiltersX
import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.FiltersXValidateField
import org.fenrirs.relay.service.nip01.Transform.convertToFiltersXObject
import org.fenrirs.relay.service.nip01.Transform.toEvent
import org.fenrirs.relay.service.nip01.Transform.validateElement

/**
 * CommandFactory เป็นอ็อบเจกต์ที่ใช้ในการประมวลผลคำสั่งที่ส่งมาจากไคลเอนต์
 */
object CommandFactory {


    /**
     * parse ใช้ในการแยกและวิเคราะห์คำสั่งที่ส่งมาจากไคลเอนต์
     * @param payload ข้อมูล JSON ที่เป็นคำสั่ง
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน (เช่น สถานะการประมวลผลเป็น true หมายถึงสำเร็จ และข้อความเตือนว่าเกิดข้อผิดพลาด)
     */
    fun parse(payload: String): Pair<Command?, Pair<Boolean, String>> {
        val jsonElement = try {
            Json.parseToJsonElement(payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid: JSON format")
        }

        if (jsonElement !is JsonArray || jsonElement.isEmpty()) {
            throw IllegalArgumentException("Invalid: command format")
        }

        return when (val cmd = jsonElement[0].jsonPrimitive.content) {
            "EVENT" -> eventCommand(jsonElement)
            "REQ" -> reqCommand(jsonElement)
            "CLOSE" -> closeCommand(jsonElement)
            "AUTH" -> TODO("Not yet implemented")
            else -> throw IllegalArgumentException("Unknown command: $cmd")
        }
    }


    /**
     * eventCommand ใช้ในการแยกและวิเคราะห์คำสั่งประเภท EVENT
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท EVENT
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน
     */
    private fun eventCommand(jsonArray: JsonArray): Pair<Command, Pair<Boolean, String>> {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonObject) {
            throw IllegalArgumentException("Invalid: EVENT command format")
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
    private fun reqCommand(jsonArray: JsonArray): Pair<Command, Pair<Boolean, String>> {
        if (jsonArray.size < 3 || jsonArray[1] !is JsonPrimitive || jsonArray.drop(2).any { it !is JsonObject }) {
            throw IllegalArgumentException("Invalid: REQ command format")
        }
        val subscriptionId: String = jsonArray[1].jsonPrimitive.content
        val filtersJson: List<JsonObject> = jsonArray.drop(2).map { it.jsonObject }

        val data: Map<String, JsonElement> = filtersJson.flatMap { it.entries }.associate { it.key to it.value }

        val filtersX: List<FiltersX> = filtersJson.map { convertToFiltersXObject(it.jsonObject) }

        val (status, warning) = validateElement(data, FiltersXValidateField.entries.toTypedArray())
        return REQ(subscriptionId, filtersX) to (status to warning)
    }


    /**
     * closeCommand ใช้ในการแยกและวิเคราะห์คำสั่งประเภท CLOSE
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท CLOSE
     * @return Pair ที่มีค่าเป็นคำสั่งและ Pair ที่มีค่าเป็นสถานะการประมวลผลและข้อความเตือน
     */
    private fun closeCommand(jsonArray: JsonArray): Pair<Command, Pair<Boolean, String>> {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonPrimitive) {
            throw IllegalArgumentException("Invalid: CLOSE command format")
        }
        val subscriptionId = jsonArray[1].jsonPrimitive.content
        return CLOSE(subscriptionId) to (true to "")
    }


}