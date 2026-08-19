package org.fenrirs.relay.core.nip.nip01.response

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.fenrirs.relay.models.Event

import org.fenrirs.relay.core.nip.nip01.command.CountREQ
import org.fenrirs.relay.core.nip.nip01.command.ApproximateCountREQ

object RelayResponseSerializer : KSerializer<RelayResponse<*>> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("RelayResponse")

    override fun serialize(encoder: Encoder, value: RelayResponse<*>) {
        val jsonEncoder =
            encoder as? JsonEncoder ?: throw SerializationException("Only JSON encoding is supported")

        val jsonObject = when (value) {

            is RelayResponse.EVENT -> JsonArray(
                listOf(
                    JsonPrimitive("EVENT"),
                    JsonPrimitive(value.subscriptionId),
                    jsonEncoder.json.encodeToJsonElement(Event.serializer(), value.event)
                )
            )

            is RelayResponse.COUNT -> {
                val countJsonElement = when (val countResponse = value.countResponse) {
                    is CountREQ -> jsonEncoder.json.encodeToJsonElement(CountREQ.serializer(), countResponse)
                    is ApproximateCountREQ -> jsonEncoder.json.encodeToJsonElement(ApproximateCountREQ.serializer(), countResponse)
                    else -> throw SerializationException("Unknown countResponse type")
                }

                JsonArray(
                    listOf(
                        JsonPrimitive("COUNT"),
                        JsonPrimitive(value.subscriptionId),
                        countJsonElement
                    )
                )
            }


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

            is RelayResponse.CANCEL -> JsonArray(
                listOf(
                    JsonPrimitive("CLOSED"),
                    JsonPrimitive(value.subscriptionId),
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

            is RelayResponse.AUTH -> JsonArray(
                listOf(
                    JsonPrimitive("AUTH"),
                    JsonPrimitive(value.challenge)
                )
            )

        }

        jsonEncoder.encodeJsonElement(jsonObject)
    }

    override fun deserialize(decoder: Decoder): RelayResponse<*> {
        throw SerializationException("Deserialization not supported")
    }

}