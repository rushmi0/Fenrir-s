package org.fenrirs.relay.core.nip01.response

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.fenrirs.relay.policy.Event


object RelayResponseSerializer : KSerializer<RelayResponse<*>> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelayResponse")

    /**
     * ฟังก์ชัน serialize ใช้ในการแปลง RelayResponse<*> เป็น JSON
     * @param encoder ใช้ในการ encode ข้อมูลเป็น JSON
     * @param value ข้อมูลประเภท RelayResponse<*> ที่ต้องการแปลงเป็น JSON
     * @throws SerializationException ถ้า encoder ไม่ใช่ JsonEncoder หรือประเภทของ value ไม่รู้จัก
     */
    override fun serialize(encoder: Encoder, value: RelayResponse<*>) {
        // ตรวจสอบว่า encoder เป็น JsonEncoder หรือไม่
        val jsonEncoder =
            encoder as? JsonEncoder ?: throw SerializationException("Only JSON encoding is supported")

        // สร้าง JsonObject ตามประเภทของ RelayResponse
        val jsonObject = when (value) {

            is RelayResponse.EVENT -> JsonArray(
                listOf(
                    JsonPrimitive("EVENT"),
                    JsonPrimitive(value.subscriptionId),
                    jsonEncoder.json.encodeToJsonElement(Event.serializer(), value.event)
                )
            )

            is RelayResponse.OK -> JsonArray(
                listOf(
                    JsonPrimitive("OK"),
                    JsonPrimitive(value.eventId),
                    JsonPrimitive(value.isSuccess),
                    JsonPrimitive(value.message)
                )
            )

            is RelayResponse.EOSE -> JsonArray(
                listOf(
                    JsonPrimitive("EOSE"),
                    JsonPrimitive(value.subscriptionId)
                )
            )

            is RelayResponse.CLOSED -> JsonArray(
                listOf(
                    JsonPrimitive("CLOSED"),
                    JsonPrimitive(value.subscriptionId),
                    JsonPrimitive(value.message)
                )
            )

            is RelayResponse.NOTICE -> JsonArray(
                listOf(
                    JsonPrimitive("NOTICE"),
                    JsonPrimitive(value.message)
                )
            )

            // กรณีประเภทที่ไม่รู้จัก
            else -> throw SerializationException("Unknown type")
        }

        // แปลง jsonObject เป็น JSON
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): RelayResponse<*> {
        throw SerializationException("Deserialization not supported")
    }
}
