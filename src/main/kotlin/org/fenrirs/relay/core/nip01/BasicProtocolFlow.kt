package org.fenrirs.relay.core.nip01

import io.micronaut.context.annotation.Bean
import io.micronaut.websocket.WebSocketSession

import jakarta.inject.Inject
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

import kotlinx.coroutines.runBlocking
import org.fenrirs.relay.core.nip01.command.COUNT
import org.fenrirs.relay.core.nip01.command.REQ
import org.slf4j.LoggerFactory

import org.fenrirs.relay.policy.Event
import org.fenrirs.relay.policy.FiltersX

import org.fenrirs.relay.core.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip01.command.CountREQ
import org.fenrirs.relay.core.nip01.command.ApproximateCountREQ
import org.fenrirs.relay.core.nip09.EventDeletion
import org.fenrirs.relay.core.nip13.ProofOfWork

import org.fenrirs.storage.NostrRelayConfig
import org.fenrirs.storage.Subscription.getSubscription
import org.fenrirs.storage.Subscription.isSubscriptionActive
import org.fenrirs.storage.Subscription.saveSubscription
import org.fenrirs.storage.statement.StoredServiceImpl

import org.fenrirs.utils.Color.GREEN
import org.fenrirs.utils.Color.PURPLE
import org.fenrirs.utils.Color.RED
import org.fenrirs.utils.Color.RESET
import org.fenrirs.utils.Color.YELLOW

@Bean
class BasicProtocolFlow @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val nip09: EventDeletion,
    private val nip13: ProofOfWork,
    private val env: NostrRelayConfig
) {

    /**
     * ฟังก์ชัน onEvent ใช้ในการจัดการเหตุการณ์ที่มีการส่งเข้ามาทาง WebSocket
     *
     * @param event เหตุการณ์ที่มีการส่งเข้ามา
     * @param status สถานะของการส่งเข้ามา (true หรือ false)
     * @param warning ข้อความแจ้งเตือน (ถ้ามี)
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    fun onEvent(event: Event, status: Boolean, warning: String, session: WebSocketSession) = runBlocking {
        //LOG.info("Received event: $event")

        if (!status) {
            // ส่งคำตอบกลับให้ไคลเอนต์ว่าไม่สามารถดำเนินการได้เพราะอะไร
            RelayResponse.OK(event.id!!, false, warning).toClient(session)
        }

        // ดึงข้อมูล public key ของ relay owner และรายการ passList จาก .env
        val relayOwner = env.RELAY_OWNER
        val passList: List<String> = getPassList(relayOwner)
        val followsPass: Boolean = env.FOLLOWS_PASS
        val work: Boolean = env.PROOF_OF_WORK_ENABLED
        val allPass: Boolean = env.ALL_PASS

        // ดักจับเหตุการณ์ เพื่อตรวจสอบนโยบายการใช้งานตามเงื่อนไขที่กำหนด
        when {

            allPass && !followsPass -> handlePassListEvent(event, session)

            // ไม่ต้องทำ Proof of Work ถ้าหาก pass เป็น true และ event.pubkey อยู่ใน passList
            followsPass && event.pubkey in passList -> handlePassListEvent(event, session)

            // บังคับทำ Proof of Work ถ้าหาก work เป็น true และ event.pubkey ไม่อยู่ใน passList
            work && event.pubkey !in passList -> handleEventWithPolicy(event, session, work)

            // บังคับทำ Proof of Work ถ้า pass เป็น false และ event.pubkey ไม่เท่ากับ relayOwner
            !followsPass && event.pubkey != relayOwner -> handleEventWithPolicy(event, session, work)

            else -> RelayResponse.OK(event.id!!, false, "blocked: no permission").toClient(session)
        }
    }


    /**
     * ฟังก์ชัน getPassList ใช้ในการดึงรายการ public key หรือผู้คนที่เจ้าของ Relay ติดตามอยู่จากฐานข้อมูล
     *
     * @param publicKey คีย์สาธารณะของเจ้าของ Relay
     * @return รายการของ public key ที่ติดตามโดยเจ้าของ Relay หากไม่พบข้อมูลจะคืนค่าเป็นรายการที่มี publicKey เพียงตัวเดียว
     */
    private suspend fun getPassList(publicKey: String): List<String> =
        sqlExec.filterList(FiltersX(authors = setOf(publicKey), kinds = setOf(3)))
            ?.firstOrNull()
            ?.tags
            ?.filter { it.isNotEmpty() && it[0] == "p" }
            ?.map { it[1] }
            ?.plus(publicKey)
            ?: listOf(publicKey)


    /**
     * ฟังก์ชัน handleDuplicateEvent ใช้ในการจัดการเหตุการณ์ที่มี ID ซ้ำกันอยู่แล้วในฐานข้อมูล
     *
     * @param event เหตุการณ์ที่มี ID ซ้ำ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับSF
     */
    private fun handleDuplicateEvent(event: Event, session: WebSocketSession) {
        LOG.info("Event kind: ${PURPLE}[${event.kind}] ${RESET}already exists in the database")
        RelayResponse.OK(event.id!!, false, "duplicate: already have this event").toClient(session)
    }


    /**
     * ฟังก์ชัน handleEventWithPolicy ใช้ในการจัดการเหตุการณ์ตามนโยบายที่กำหนด
     *
     * @param event เหตุการณ์ที่ต้องการจัดการ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     * @param enabled สถานะของนโยบาย Proof of Work ที่เปิดหรือปิด
     */
    private suspend fun handleEventWithPolicy(event: Event, session: WebSocketSession, enabled: Boolean) {
        if (nip09.validDeletion(event)) {
            throw IllegalArgumentException("blocked: event rejected")
        }

        val eventId: Event? = sqlExec.selectById(event.id!!)
        when {
            eventId != null -> handleDuplicateEvent(event, session)
            nip09.isDeletable(event) -> handleDeletableEvent(event, session)
            else -> handleProofOfWorkEvent(event, session, enabled)
        }
    }


    /**
     * ฟังก์ชัน handlePassListEvent ใช้ในการจัดการเหตุการณ์ที่ผ่านการตรวจสอบ Pass List
     *
     * @param event เหตุการณ์ที่ผ่านการตรวจสอบ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend fun handlePassListEvent(event: Event, session: WebSocketSession) {
        if (nip09.validDeletion(event)) {
            throw IllegalArgumentException("blocked: event rejected")
        }

        val eventId: Event? = sqlExec.selectById(event.id!!)
        when {
            eventId != null -> handleDuplicateEvent(event, session)

            nip13.isProofOfWorkEvent(event) -> handleProofOfWorkEvent(event, session)
            nip09.isDeletable(event) -> handleDeletableEvent(event, session)
            else -> handleNormalEvent(event, session)
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////


    /**
     * ฟังก์ชัน handleEvent ใช้ในการจัดการเหตุการณ์ที่มีการดำเนินการตามสถานะที่ได้รับ
     *
     * @param event เหตุการณ์ที่ต้องการจัดการ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     * @param action ลำดับการดำเนินการที่ต้องทำ
     */
    private suspend fun handleEvent(
        event: Event,
        session: WebSocketSession,
        action: suspend () -> Pair<Boolean, String>
    ) {
        try {
            val (success, message) = action.invoke()

            if (success) {
                LOG.info("${session.id} handled ${GREEN}saved, ${RESET}Event${YELLOW}<${event.kind}>${RESET}ID: ${PURPLE}[${event.id}]${RESET}")
                RelayResponse.OK(event.id!!, true, message).toClient(session)
            } else {
                LOG.warn("${RED}Failed ${RESET}to handle event: ${event.id}")
                RelayResponse.OK(event.id!!, false, message).toClient(session)
            }

        } catch (e: Exception) {
            LOG.error("Error handling event: ${event.id}", e)
            RelayResponse.NOTICE("error: ${e.message}").toClient(session)
        }
    }


    /**
     * ฟังก์ชัน handleNormalEvent ใช้ในการจัดการเหตุการณ์ทั่วไป
     *
     * @param event เหตุการณ์ที่ต้องการจัดการ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend fun handleNormalEvent(event: Event, session: WebSocketSession) {
        handleEvent(event, session) {
            val status: Boolean = sqlExec.saveEvent(event)
            status to (if (status) "" else "error: could not save event to the database")
        }
    }


    /**
     * ฟังก์ชัน handleDeletableEvent ใช้ในการจัดการเหตุการณ์ที่ต้องการลบข้อมูลตามที่กำหนด
     *
     * @param event เหตุการณ์ที่สามารถลบได้
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend fun handleDeletableEvent(event: Event, session: WebSocketSession) {
        require(nip09.isOwnership(event)) { "blocked: no permission to delete" }

        handleEvent(event, session) {
            val (deletionSuccess, message) = nip09.deleteEvent(event)
            if (deletionSuccess) {
                val status: Boolean = sqlExec.saveEvent(event)
                status to (if (status) message else "error: could not save event to the database after deletion")
            } else {
                false to message
            }
        }
    }


    /**
     * ฟังก์ชัน handleProofOfWorkEvent ใช้ในการจัดการเหตุการณ์ที่มีการสร้าง Proof of Work
     *
     * @param event เหตุการณ์ที่ต้องการจัดการ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     * @param enabled สถานะของ Proof of Work ที่เปิดหรือปิดที่ถูกกำหนดในไฟล์กำหนดตามนโยบาย
     */
    private suspend fun handleProofOfWorkEvent(event: Event, session: WebSocketSession, enabled: Boolean = false) {
        handleEvent(event, session) {
            val (valid, message) = nip13.verifyProofOfWork(event, enabled)
            if (valid) {
                val status: Boolean = sqlExec.saveEvent(event)
                status to (if (status) "" else "error: could not save Proof of Work event")
            } else {
                false to message
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////


    /**
     * ฟังก์ชัน onRequest ใช้ในการจัดการการร้องขอข้อมูลจากไคลเอนต์ที่เชื่อมต่อผ่าน WebSocket
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param filtersX คำขอข้อมูลที่ไคลเอนต์ต้องการ
     * @param status สถานะของการร้องขอ (true หรือ false)
     * @param warning ข้อความแจ้งเตือน (ถ้ามี)
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    suspend fun onRequest(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        status: Boolean,
        warning: String,
        session: WebSocketSession
    ) {
        if (status) {
            if (isSubscriptionActive(session, subscriptionId)) {
                // ถ้า subscriptionId ซ้ำกับในระบบที่มีอยู่
                RelayResponse.CANCEL("duplicate: $subscriptionId already opened").toClient(session)
            } else {
                // ถ้า subscriptionId ไม่ซ้ำ
                saveSubscription(session, subscriptionId, filtersX)
                handleValidRequest(subscriptionId, filtersX, session)
            }
        } else {
            RelayResponse.NOTICE(warning).toClient(session)
        }
    }


    /**
     * ฟังก์ชัน handleValidRequest ใช้ในการจัดการการร้องขอที่มีสถานะเป็น true
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param filtersX คำขอข้อมูลที่ไคลเอนต์ต้องการ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend fun handleValidRequest(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        session: WebSocketSession
    ) {
        //LOG.info("${GREEN}filters ${YELLOW}[${filtersX.size}] ${RESET}req subscription ID: ${CYAN}$subscriptionId ${RESET}")
        //LOG.info("FiltersX: $filtersX")

        filtersX.forEach { filter ->
            sqlExec.filterList(filter)?.forEach { event ->
                RelayResponse.EVENT(subscriptionId, event).toClient(session)
            }
        }
        RelayResponse.EOSE(subscriptionId).toClient(session)
        startRealTimeUpdates<REQ>(subscriptionId, session)
    }


    /**
     * ฟังก์ชัน onCount ใช้ในการจัดการนับจำนวนข้อมูลที่ไคลเอนต์ต้องการ
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param filtersX คำขอข้อมูลที่ไคลเอนต์ต้องการ
     * @param status สถานะของการร้องขอ (true หรือ false)
     * @param warning ข้อความแจ้งเตือน (ถ้ามี)
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    suspend fun onCount(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        status: Boolean,
        warning: String,
        session: WebSocketSession
    ) {
        if (status) {
            if (isSubscriptionActive(session, subscriptionId)) {
                RelayResponse.CANCEL("duplicate: $subscriptionId already opened").toClient(session)
            } else {
                saveSubscription(session, subscriptionId, filtersX)
                handleValidCount(subscriptionId, filtersX, session)
            }
        } else {
            RelayResponse.NOTICE(warning).toClient(session)
        }
    }


    /**
     * ฟังก์ชัน handleValidCount ใช้ในการจัดการการร้องขอนับจำนวนที่มีสถานะเป็น true
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param filtersX คำขอข้อมูลที่ไคลเอนต์ต้องการ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend fun handleValidCount(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        session: WebSocketSession
    ) {
        filtersX.forEach { filter ->
            val count = sqlExec.filterList(filter)?.size ?: 0

            val response = when {
                count < 0 -> throw IllegalArgumentException("Count cannot be negative")
                count >= 93_412_452 -> ApproximateCountREQ(93_412_452, true)
                else -> CountREQ(count)
            }

            RelayResponse.COUNT(subscriptionId, response).toClient(session)
        }

        // แจ้งว่าเสร็จสิ้นการนับจำนวนเหตุการณ์
        RelayResponse.EOSE(subscriptionId).toClient(session)
        startRealTimeUpdates<COUNT>(subscriptionId, session)
    }



    /**
     * ฟังก์ชัน startRealTimeUpdates ใช้ในการเริ่มต้นการอัปเดตข้อมูลแบบเรียลไทม์
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend inline fun <reified T> startRealTimeUpdates(subscriptionId: String, session: WebSocketSession) {
        var lastUpdateTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

        // แสดงชื่อคลาสของ T
        //LOG.info("Option: ${T::class.simpleName}")
        do {
            val currentTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            val updatedFiltersX = getSubscription(session, subscriptionId).map {
                it.copy(
                    since = lastUpdateTime,
                    until = currentTime
                )
            }


            updatedFiltersX.forEach { filter ->

                // ตรวจสอบว่า events ไม่เป็น null และไม่ใช่ list ว่าง
                sqlExec.filterList(filter).takeIf { !it.isNullOrEmpty() }?.let { events ->
                    when (T::class) {
                        COUNT::class -> {
                            val count = events.size
                            val data = if (count >= 93_412_452) ApproximateCountREQ(93_412_452, true) else CountREQ(count)
                            RelayResponse.COUNT(subscriptionId, data).toClient(session)
                        }
                        REQ::class -> events.forEach { event ->
                            RelayResponse.EVENT(subscriptionId, event).toClient(session)
                        }
                    }
                }

            }

            delay(1400)

            // อัปเดต lastUpdateTime หลังจากค้นหาข้อมูลเสร็จ
            lastUpdateTime = currentTime

        } while (isSubscriptionActive(session, subscriptionId))
    }


    /**
     * ฟังก์ชัน onClose ใช้ในการจัดการคำขอปิดการเชื่อมต่อ WebSocket
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    fun onClose(subscriptionId: String, session: WebSocketSession) {
        //LOG.info("${YELLOW}cancel ${RESET}subscription ID: $subscriptionId")
        RelayResponse.CANCEL(subscriptionId).toClient(session)
    }


    /**
     * ฟังก์ชัน onUnknown ใช้ในการปิดการเชื่อมต่อ เพื่อจัดการคำสั่งที่ไม่รู้จักที่เข้ามาผ่าน WebSocket
     *
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    fun onUnknown(session: WebSocketSession) {
        LOG.warn("${RED}Unknown command")
        RelayResponse.NOTICE("Unknown command").toClient(session); session.close()
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(BasicProtocolFlow::class.java)
    }

}