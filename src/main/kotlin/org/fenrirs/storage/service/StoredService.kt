package org.fenrirs.storage.service

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX

enum class SaveOutcome { SAVED, DUPLICATE, FAILED }

data class KindCount(val kind: Int, val count: Long)

/** One bucket in the daily-activity trend; [dayStart] is the bucket's start as a Unix second. */
data class DailyCount(val dayStart: Long, val count: Long)

/** Events carrying a given value of the informal (non-NIP-01) `client` tag several Nostr apps
 * self-attribute their published events with - not every event has one, see [EventStats.totalEvents]
 * for the untagged remainder. */
data class ClientCount(val client: String, val count: Long)

data class EventStats(
    val totalEvents: Long,
    val totalAuthors: Long,
    /** Distinct pubkeys with a kind-0 (metadata/profile) event on this relay - "how many users",
     * as opposed to [totalAuthors]'s "how many pubkeys have published anything at all". */
    val totalUsers: Long,
    val oldestEventAt: Long?,
    val kindCounts: List<KindCount>,
    val dailyCounts: List<DailyCount>,
    val clientCounts: List<ClientCount>
)

/** Same shape as [EventStats] minus [EventStats.totalAuthors] - always 1 and meaningless when
 * scoped to a single author (see [StoredService.eventStatsForAuthor]). */
data class AuthorEventStats(
    val totalEvents: Long,
    val oldestEventAt: Long?,
    val kindCounts: List<KindCount>,
    val dailyCounts: List<DailyCount>
)

data class ProfileEvent(val pubkeyHex: String, val createdAt: Long, val content: String)

interface StoredService {

    /**
     * saveEvent ใช้ในการบันทึกเหตุการณ์ลงในฐานข้อมูล
     * @param event เหตุการณ์ที่ต้องการบันทึก
     * @return ค่าเป็น true หากการบันทึกสำเร็จ และ false หากไม่สำเร็จ
     */
    suspend fun saveEvent(event: Event): Boolean

    /**
     * saveIfAbsent พยายามบันทึกเหตุการณ์ในทรานแซกชันเดียว โดยอาศัย unique index บน EVENT_ID
     * เพื่อตรวจจับ duplicate แทนการ SELECT เช็คก่อน INSERT แยกทรานแซกชัน
     * @param event เหตุการณ์ที่ต้องการบันทึก
     * @return [SaveOutcome.SAVED], [SaveOutcome.DUPLICATE], หรือ [SaveOutcome.FAILED]
     */
    suspend fun saveIfAbsent(event: Event): SaveOutcome

    /**
     * deleteEvent ใช้ในการลบเหตุการณ์จากฐานข้อมูล
     * @param eventId ไอดีของเหตุการณ์ที่ต้องการลบ
     * @return ค่าเป็น true หากการลบสำเร็จ และ false หากไม่สำเร็จ
     */
    suspend fun deleteEvent(eventId: String): Boolean

    /**
     * selectById ใช้ในการเลือกเหตุการณ์จากฐานข้อมูลโดยใช้ไอดี
     * @param id ไอดีของเหตุการณ์ที่ต้องการเลือก
     * @return เหตุการณ์ที่เลือก หากพบ หรือ null หากไม่พบ
     */
    suspend fun selectById(id: String): Event?

    /**
     * filterList ใช้ในการดึงรายการข้อมูล Event จากฐานข้อมูลตามเงื่อนไขที่ระบุใน FiltersX
     * @param filters เงื่อนไขการคัดกรองข้อมูล ตามที่ไคลเอนต์ต้องการ
     * @return รายการเหตุการณ์ที่ตรงกับเงื่อนไข
     */
    suspend fun filterList(filters: FiltersX): List<Event>?

    /**
     * eventStats รวบรวมสถิติของตาราง EVENT สำหรับหน้า Dashboard ของแอดมิน
     * @param sinceDays จำนวนวันย้อนหลังที่ต้องการนับ dailyCounts
     * @return สรุปจำนวน event ทั้งหมด, จำนวนผู้เขียนที่ไม่ซ้ำกัน, event เก่าสุด, สัดส่วนตาม kind และแนวโน้มรายวัน
     */
    suspend fun eventStats(sinceDays: Int): EventStats

    /**
     * eventStatsForAuthor รวบรวมสถิติของ event ทั้งหมดจาก pubkey เดียว สำหรับแท็บ "Activity" ของ
     * หน้า Accounts ในฝั่งแอดมิน - เหมือนกับ [eventStats] ทุกประการแต่กรองด้วย pubkey
     * @param pubkeyHex hex pubkey ของ author ที่ต้องการดูสถิติ
     * @param sinceDays จำนวนวันย้อนหลังที่ต้องการนับ dailyCounts
     */
    suspend fun eventStatsForAuthor(pubkeyHex: String, sinceDays: Int): AuthorEventStats

    suspend fun latestProfileEvents(limit: Int): List<ProfileEvent>

}