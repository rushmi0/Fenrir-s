package org.fenrirs.relay.core.nip01.command


import org.fenrirs.relay.policy.Event
import org.fenrirs.relay.policy.FiltersX
import kotlinx.serialization.Serializable

/**
 * Command เป็นคลาสฐานที่ใช้สำหรับกำหนดรูปแบบคำสั่งที่ไคลเอนต์สามารถส่งเข้ามาได้
 * โดยคำสั่งแต่ละประเภทจะถูกสร้างเป็น subclass ของ Command ซึ่งเป็น API ที่ใช้ในการสื่อสารกับไคลเอนต์
 */
@Serializable
sealed class Command

/**
 * EVENT ใช้สำหรับส่งเหตุการณ์ใหม่จากไคลเอนต์มาที่รีเลย์
 * @param event เหตุการณ์ที่ถูกส่งมาจากไคลเอนต์
 */
@Serializable
data class EVENT(val event: Event) : Command()

/**
 * REQ ใช้สำหรับส่งคำขอข้อมูลจากไคลเอนต์ โดยไคลเอนต์สามารถระบุเงื่อนไขการกรองข้อมูลได้
 * @param subscriptionId ไอดีสำหรับติดตามคำขอ
 * @param filtersX เงื่อนไขในการกรองข้อมูล
 */
@Serializable
data class REQ(val subscriptionId: String, val filtersX: List<FiltersX>) : Command()

/**
 * CLOSE ใช้สำหรับไคลเอนต์ในการปิดการติดตามข้อมูล (ยกเลิก subscription)
 * @param subscriptionId ไอดีสำหรับติดตามคำขอที่ต้องการปิด
 */
@Serializable
data class CLOSE(val subscriptionId: String) : Command()

/**
 * AUTH ใช้สำหรับการตรวจสอบสิทธิ์ของไคลเอนต์ด้วยการส่ง challenge เพื่อให้ไคลเอนต์ทำการพิสูจน์ตัวตน
 * @param challenge ข้อมูลที่ใช้ในการพิสูจน์ตัวตน
 */
@Serializable
data class AUTH(val challenge: String) : Command()

/**
 * COUNT ใช้สำหรับส่งคำขอจำนวนของเหตุการณ์ที่ตรงกับเงื่อนไขการร้องขอจากไคลเอนต์
 * @param subscriptionId ไอดีสำหรับติดตามคำขอ
 * @param filtersX เงื่อนไขในการกรองข้อมูล
 */
@Serializable
data class COUNT(val subscriptionId: String, val filtersX: List<FiltersX>) : Command()

@Serializable
data class CountREQ(val count: Int)

@Serializable
data class ApproximateCountREQ(val count: Int, val approximate: Boolean)