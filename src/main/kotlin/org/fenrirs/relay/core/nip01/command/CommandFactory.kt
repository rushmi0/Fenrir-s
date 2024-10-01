package org.fenrirs.relay.core.nip01.command

import kotlinx.serialization.json.*
import org.fenrirs.relay.core.nip01.CommandParseResult
import org.fenrirs.relay.core.nip01.EventCommandResult
import org.fenrirs.relay.core.nip01.FiltersData

import org.fenrirs.relay.policy.Event
import org.fenrirs.relay.policy.FiltersX

import org.fenrirs.relay.policy.EventValidateField
import org.fenrirs.relay.policy.FiltersXValidateField

import org.fenrirs.relay.core.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip01.Transform.toFiltersX
import org.fenrirs.relay.core.nip01.Transform.validateElement
import org.fenrirs.relay.policy.NostrRelayConfig
import org.fenrirs.storage.Environment


object CommandFactory {

    private val env: Environment by lazy { Environment(NostrRelayConfig()) }

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
            "REQ" -> parseREQ(jsonElement)
            "CLOSE" -> parseClose(jsonElement)
            //"AUTH" -> TODO("Not yet implemented.")
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
        val data: FiltersData = eventJson.toMap()

        val validationResult = validateElement(data, EventValidateField.entries.toTypedArray())
        return EVENT(event) to validationResult
    }


    /**
     * ฟังก์ชัน parseREQ ใช้ในการแยกและวิเคราะห์คำสั่งประเภท REQ
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท REQ
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง REQ และผลการตรวจสอบความถูกต้อง
     * ฟังก์ชันนี้จะทำการตรวจสอบจำนวน filters ที่กำหนดใน env.MAX_FILTERS
     * และตรวจสอบความถูกต้องของข้อมูล filtersX
     */
    private fun parseREQ(jsonArray: JsonArray): EventCommandResult {
        if (jsonArray.size < 3 || jsonArray[1] !is JsonPrimitive || jsonArray.drop(2).any { it !is JsonObject }) {
            throw IllegalArgumentException("invalid: REQ command format")
        }
        val subscriptionId: String = jsonArray[1].jsonPrimitive.content
        val filtersJson: List<JsonObject> = jsonArray.drop(2).map { it.jsonObject }

        if (filtersJson.size > env.MAX_FILTERS) {
            throw IllegalArgumentException("rate-limited: max filters ${env.MAX_FILTERS} values in each sub ID allowed")
        }

        val data: FiltersData = filtersJson.flatMap { it.entries }.associate { it.key to it.value }

        val filtersX: List<FiltersX> = filtersJson.map { it.toFiltersX() }

        val validationResult = validateElement(data, FiltersXValidateField.entries.toTypedArray())
        return REQ(subscriptionId, filtersX) to validationResult
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
