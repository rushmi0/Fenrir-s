package org.fenrirs.relay.core.nip.nip01.command


import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX
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
 * AUTH (client -> relay) ใช้ส่ง event ที่เซ็นชื่อแล้ว (kind 22242) กลับมาเพื่อพิสูจน์ตัวตนตาม NIP-42
 * เพื่อตอบสนอง challenge ที่ relay ส่งไปก่อนหน้า (relay -> client ส่งเป็น RelayResponse.AUTH แทน)
 * @param event auth event ที่ไคลเอนต์เซ็นชื่อส่งมา
 */
@Serializable
data class AUTH(val event: Event) : Command()

/**
 * COUNT ใช้สำหรับส่งคำขอจำนวนของเหตุการณ์ที่ตรงกับเงื่อนไขการร้องขอจากไคลเอนต์
 * @param subscriptionId ไอดีสำหรับติดตามคำขอ
 * @param filtersX เงื่อนไขในการกรองข้อมูล
 */
@Serializable
data class COUNT(val subscriptionId: String, val filtersX: List<FiltersX>) : Command()

/**
 * CountREQ ใช้เพื่อส่งคำขอสำหรับการนับจำนวนเหตุการณ์ที่ตรงตามเงื่อนไขที่ระบุ
 * ในคำขอนี้จะมีข้อมูลจำนวนเต็มที่บ่งบอกถึงจำนวนเหตุการณ์ที่ต้องการนับ
 * @param count จำนวนเหตุการณ์ที่ต้องการ
 */
@Serializable
data class CountREQ(val count: Int)

/**
 * ApproximateCountREQ ใช้สำหรับส่งคำขอที่มีการประมาณจำนวนเหตุการณ์
 * นอกจากจำนวนเหตุการณ์ที่ต้องการนับแล้ว ยังมีการระบุว่าเป็นการประมาณหรือไม่
 * @param count จำนวนเหตุการณ์ที่ต้องการ
 * @param approximate ตัวแปร Boolean ที่บอกว่าการนับนั้นเป็นการประมาณหรือไม่
 */
@Serializable
data class ApproximateCountREQ(val count: Int, val approximate: Boolean)
