package org.fenrirs.storage.service

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX

enum class SaveOutcome { SAVED, DUPLICATE, FAILED }

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



}