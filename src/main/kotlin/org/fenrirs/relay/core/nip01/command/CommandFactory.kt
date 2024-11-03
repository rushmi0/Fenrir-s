package org.fenrirs.relay.core.nip01.command

import kotlinx.serialization.json.*
import org.fenrirs.relay.core.nip01.CommandParseResult
import org.fenrirs.relay.core.nip01.EventCommandResult

import org.fenrirs.relay.policy.Event
import org.fenrirs.relay.policy.FiltersX

import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.FiltersXValidateField

import org.fenrirs.relay.core.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip01.Transform.toFiltersX
import org.fenrirs.relay.core.nip01.Transform.validateElement
import org.fenrirs.storage.Environment

object CommandFactory {

    private val env: Environment by lazy {
        Environment()
    }

    /**
     * ฟังก์ชัน parse ใช้ในการแยกและวิเคราะห์คำสั่งที่ส่งมาจากไคลเอนต์
     * @param payload ข้อมูล JSON ที่เป็นคำสั่งจากไคลเอนต์
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง (Command) และผลการตรวจสอบความถูกต้อง (ValidationResult)
     * ฟังก์ชันนี้จะทำการตรวจสอบรูปแบบของ JSON และระบุประเภทคำสั่ง (เช่น EVENT, REQ, CLOSE)
     * แล้วส่งต่อไปยังฟังก์ชันที่เหมาะสมเพื่อประมวลผลคำสั่งนั้น ๆ
     */
    fun parse(payload: String): CommandParseResult {
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
            "REQ" -> parseSubWithFilters<REQ>(jsonElement)
            "COUNT" -> parseSubWithFilters<COUNT>(jsonElement)
            "CLOSE" -> parseClose(jsonElement)
            // "AUTH" -> TODO("Not yet implemented")
            else -> throw IllegalArgumentException("Unknown command: $cmd")
        }
    }


    /**
     * ฟังก์ชัน parseEvent ใช้ในการแยกและวิเคราะห์คำสั่งประเภท EVENT
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท EVENT
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง EVENT และผลการตรวจสอบความถูกต้อง
     * ฟังก์ชันนี้จะทำการแปลงข้อมูล JSON เป็นอ็อบเจ็กต์ Event และตรวจสอบความถูกต้องของข้อมูล
     */
    private fun parseEvent(jsonArray: JsonArray): EventCommandResult {
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
     * ฟังก์ชัน parseSubWithFilters ใช้ในการแยกและวิเคราะห์คำสั่งประเภท REQ และ COUNT
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท REQ หรือ COUNT
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง (REQ หรือ COUNT) และผลการตรวจสอบความถูกต้อง
     * ฟังก์ชันนี้ทำการตรวจสอบฟิลด์ subscriptionId และ filters และทำการ validate ข้อมูล filter
     */
    private inline fun <reified T : Command> parseSubWithFilters(
        jsonArray: JsonArray
    ): EventCommandResult {
        if (jsonArray.size < 3 || jsonArray[1] !is JsonPrimitive || jsonArray.drop(2).any { it !is JsonObject }) {
            throw IllegalArgumentException("invalid: ${T::class.simpleName} command format")
        }

        val subscriptionId: String = jsonArray[1].jsonPrimitive.content
        val filtersJson: List<JsonObject> = jsonArray.drop(2).map { it.jsonObject }

        // ตรวจสอบจำนวน filters ว่าไม่เกินค่าที่กำหนด
        if (filtersJson.size > env.MAX_FILTERS) {
            throw IllegalArgumentException("rate-limited: max filters ${env.MAX_FILTERS} values in each sub ID allowed")
        }

        val data: Map<String, JsonElement> = filtersJson.flatMap { it.entries }.associate { it.key to it.value }
        val filtersX: List<FiltersX> = filtersJson.map { it.jsonObject.toFiltersX() }

        // ตรวจสอบความถูกต้องของฟิลด์ใน filters
        val (status, warning) = validateElement(data, FiltersXValidateField.entries.toTypedArray())

        val command = when (T::class) {
            REQ::class -> REQ(subscriptionId, filtersX)
            COUNT::class -> COUNT(subscriptionId, filtersX)
            else -> throw IllegalArgumentException("Unsupported command type")
        }

        return command to (status to warning)
    }


    /**
     * ฟังก์ชัน parseClose ใช้ในการแยกและวิเคราะห์คำสั่งประเภท CLOSE
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท CLOSE
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง CLOSE และผลการตรวจสอบความถูกต้อง
     * ฟังก์ชันนี้จะทำการตรวจสอบรูปแบบของคำสั่ง CLOSE
     * และคืนผลลัพธ์ว่าผ่านการตรวจสอบหรือไม่
     */
    private fun parseClose(jsonArray: JsonArray): EventCommandResult {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonPrimitive) {
            throw IllegalArgumentException("invalid: CLOSE command format")
        }
        val subscriptionId = jsonArray[1].jsonPrimitive.content
        return CLOSE(subscriptionId) to (true to "")
    }


}