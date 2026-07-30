package org.fenrirs.relay.core.nip.nip01.command

import kotlinx.serialization.json.*
import org.fenrirs.relay.core.nip.nip01.CommandParseResult
import org.fenrirs.relay.core.nip.nip01.EventCommandResult
import org.fenrirs.relay.core.nip.nip01.ValidationResult

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX

import org.fenrirs.relay.models.EventValidateField
import org.fenrirs.relay.models.FiltersXValidateField

import org.fenrirs.relay.core.nip.nip01.Transform.toEvent
import org.fenrirs.relay.core.nip.nip01.Transform.toFiltersX
import org.fenrirs.relay.core.nip.nip01.Transform.validateElement
import org.fenrirs.relay.core.nip.nip01.VerifyEvent.verifyNip01
import org.fenrirs.storage.NostrRelayConfig

object CommandFactory {

    private val env: NostrRelayConfig by lazy {
        NostrRelayConfig()
    }

    private val EVENT_POLICY = EventValidateField.entries.toTypedArray()
    private val FILTERS_POLICY = FiltersXValidateField.entries.toTypedArray()

    /**
     * ฟังก์ชัน parse ใช้ในการแยกและวิเคราะห์คำสั่งที่ส่งมาจากไคลเอนต์
     * @param payload ข้อมูล JSON ที่เป็นคำสั่งจากไคลเอนต์
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง (Command) และผลการตรวจสอบความถูกต้อง (ValidationResult)
     * ฟังก์ชันนี้จะทำการตรวจสอบรูปแบบของ JSON และระบุประเภทคำสั่ง (เช่น EVENT, REQ, CLOSE)
     * แล้วส่งต่อไปยังฟังก์ชันที่เหมาะสมเพื่อประมวลผลคำสั่งนั้น ๆ
     */
    fun parse(payload: String): CommandParseResult {
        val jsonElement = runCatching { Json.parseToJsonElement(payload) }
            .getOrElse { throw IllegalArgumentException("invalid: JSON format") }

        if (jsonElement !is JsonArray || jsonElement.isEmpty()) {
            throw IllegalArgumentException("invalid: command format")
        }

        return when (val cmd = jsonElement[0].jsonPrimitive.content) {
            "EVENT" -> parseEvent(jsonElement)
            "REQ" -> parseSubWithFilters<REQ>(jsonElement)
            "COUNT" -> parseSubWithFilters<COUNT>(jsonElement)
            "CLOSE" -> parseClose(jsonElement)
            "AUTH" -> parseAuth(jsonElement)
            else -> throw IllegalArgumentException("Unknown command: $cmd")
        }
    }


    /**
     * ฟังก์ชัน parseEvent ใช้ในการแยกและวิเคราะห์คำสั่งประเภท EVENT
     * @param jsonArray JsonArray ที่มีข้อมูลเป็นคำสั่งประเภท EVENT
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง EVENT และผลการตรวจสอบความถูกต้อง
     *
     * ตรวจสอบโครงสร้าง (field name/type/required) กับ raw JSON ก่อนเสมอ เพราะราคาถูกที่สุดและปลอดภัย
     * (ใช้ `is` type-check ล้วน ไม่มีการ cast ที่ throw ได้) จากนั้นจึงสร้าง Event object เพียงครั้งเดียว
     * และตรวจสอบ NIP-01 (event id -> signature) กับ Event ตัวเดียวกันนั้น
     *
     * สำคัญ: ต้องตรวจโครงสร้างก่อนเรียก toEvent() เสมอ เพราะ toEvent() ใช้ .jsonPrimitive/.jsonArray
     * ซึ่ง throw IllegalStateException ทันทีถ้า field มี JSON type ผิด (เช่น "tags" ถูกส่งมาเป็น object
     * แทนที่จะเป็น array) — exception ชนิดนี้ไม่ถูกดักโดย Gateway ทำให้ connection ล่มได้ถ้าสร้าง Event
     * ก่อนตรวจสอบโครงสร้างเหมือนโค้ดเดิม
     */
    private fun parseEvent(jsonArray: JsonArray): EventCommandResult {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonObject) {
            throw IllegalArgumentException("invalid: EVENT command format")
        }
        val eventJson = jsonArray[1].jsonObject

        val structureCheck = validateElement(eventJson, EVENT_POLICY)
        if (!structureCheck.isValid) {
            return EVENT(Event()) to structureCheck
        }

        val event = eventJson.toEvent()
        return EVENT(event) to event.verifyNip01()
    }


    /**
     * ฟังก์ชัน parseAuth ใช้ในการแยกและวิเคราะห์คำสั่งประเภท AUTH (client -> relay) ตาม NIP-42
     * @param jsonArray JsonArray รูปแบบ ["AUTH", {event-json}] ซึ่งมีโครงสร้างเหมือน ["EVENT", {event-json}] ทุกประการ
     * @return ผลลัพธ์เป็นคู่ของคำสั่ง AUTH และผลการตรวจสอบตาม NIP-01 (id/signature/pubkey)
     *
     * ตรวจสอบเฉพาะ NIP-01 ที่นี่ (เหมือน parseEvent ทุกประการ เพราะ auth event ก็คือ Event ธรรมดาที่ kind=22242)
     * ส่วนกฎเฉพาะของ NIP-42 (kind ต้องเป็น 22242, challenge/relay tag ต้องตรงกับที่ relay ส่งไป) ตรวจสอบที่
     * BasicProtocolFlow.onAuth แทน เพราะต้องใช้ challenge ที่ผูกกับ session ซึ่ง CommandFactory ไม่มีข้อมูลนี้
     */
    private fun parseAuth(jsonArray: JsonArray): EventCommandResult {
        if (jsonArray.size != 2 || jsonArray[1] !is JsonObject) {
            throw IllegalArgumentException("invalid: AUTH command format")
        }
        val eventJson = jsonArray[1].jsonObject

        val structureCheck = validateElement(eventJson, EVENT_POLICY)
        if (!structureCheck.isValid) {
            return AUTH(Event()) to structureCheck
        }

        val event = eventJson.toEvent()
        return AUTH(event) to event.verifyNip01()
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
            throw IllegalArgumentException("rate-limited: max filter ${env.MAX_FILTERS} values each sub ID allowed")
        }

        val data: Map<String, JsonElement> = filtersJson.flatMap { it.entries }.associate { it.key to it.value }
        val filtersX: List<FiltersX> = filtersJson.map { it.toFiltersX() }

        // ตรวจสอบความถูกต้องของฟิลด์ใน filters
        val validation = validateElement(data, FILTERS_POLICY)

        val command = when (T::class) {
            REQ::class -> REQ(subscriptionId, filtersX)
            COUNT::class -> COUNT(subscriptionId, filtersX)
            else -> throw IllegalArgumentException("Unsupported command type")
        }

        return command to validation
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
        return CLOSE(subscriptionId) to ValidationResult.Valid
    }


}