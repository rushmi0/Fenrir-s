package org.fenrirs.relay.core.nip.nip01

import io.micronaut.websocket.WebSocketSession

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import org.slf4j.LoggerFactory

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX

import org.fenrirs.relay.core.nip.nip01.response.RelayResponse
import org.fenrirs.relay.core.nip.nip01.command.CountREQ
import org.fenrirs.relay.core.nip.nip01.command.ApproximateCountREQ
import org.fenrirs.relay.core.nip.nip09.EventDeletion
import org.fenrirs.relay.core.nip.nip42.VerifyAuth

import org.fenrirs.relay.core.policy.CommandType
import org.fenrirs.relay.core.policy.PolicyContext
import org.fenrirs.relay.core.policy.PolicyController
import org.fenrirs.relay.core.policy.PolicyDecision

import org.fenrirs.relay.core.pubsub.EventBus
import org.fenrirs.relay.core.pubsub.SubscriptionEntry
import org.fenrirs.relay.core.pubsub.SubscriptionRegistry

import org.fenrirs.storage.Authentication
import org.fenrirs.storage.service.SaveOutcome
import org.fenrirs.storage.statement.StoredServiceImpl


@Singleton
class BasicProtocolFlow @Inject constructor(
    private val sqlExec: StoredServiceImpl,
    private val nip09: EventDeletion,
    private val nip42: VerifyAuth,
    private val policyController: PolicyController,
    private val eventBus: EventBus,
    private val registry: SubscriptionRegistry,
    private val authentication: Authentication
) {

    private val pushScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * ฟังก์ชัน onEvent ใช้ในการจัดการเหตุการณ์ที่มีการส่งเข้ามาทาง WebSocket
     *
     * @param event เหตุการณ์ที่มีการส่งเข้ามา
     * @param status สถานะของการส่งเข้ามา (true หรือ false) จากการตรวจสอบ NIP-01 (id/signature/pubkey)
     * @param warning ข้อความแจ้งเตือน (ถ้ามี)
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    suspend fun onEvent(event: Event, status: Boolean, warning: String, session: WebSocketSession) {
        if (!status) {
            LOG.warn("[EVENT] Invalid id={} reason={}", event.id, warning)
            RelayResponse.OK(event.id!!, false, warning).toClient(session)
            return
        }

        val decision: PolicyDecision =
            policyController.evaluate(PolicyContext(session, CommandType.EVENT, event = event))
        when (decision) {
            is PolicyDecision.Allow -> ingestEvent(event, session)
            is PolicyDecision.Deny -> RelayResponse.OK(event.id!!, false, decision.reason).toClient(session)
            is PolicyDecision.RequireAuth -> RelayResponse.OK(event.id!!, false, decision.reason).toClient(session)
        }
    }


    /**
     * ฟังก์ชัน handleDuplicateEvent ใช้ในการจัดการเหตุการณ์ที่มี ID ซ้ำกันอยู่แล้วในฐานข้อมูล
     *
     * @param event เหตุการณ์ที่มี ID ซ้ำ
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับSF
     */
    private fun handleDuplicateEvent(event: Event, session: WebSocketSession) {
        LOG.warn("[EVENT] Duplicate id={} kind={}", event.id, event.kind)
        RelayResponse.OK(event.id!!, false, "duplicate: already have this event").toClient(session)
    }


    /**
     * ฟังก์ชัน ingestEvent ใช้ในการนำ event ที่ [policyController] อนุญาตแล้วเข้าสู่ฐานข้อมูล
     * ตรวจสอบเฉพาะเรื่องที่ไม่ใช่ authorization อีกต่อไป (event ซ้ำ / เป็นคำขอลบที่ผิดรูปแบบ / ลบจริง / บันทึกปกติ)
     * เพราะเรื่อง "อนุญาตหรือไม่" (auth, whitelist, pass list, proof of work) ผ่านการตัดสินใจจาก policy pipeline
     * มาก่อนที่จะเรียกฟังก์ชันนี้แล้วเสมอ
     *
     * @param event เหตุการณ์ที่ผ่านการอนุญาตจาก policy แล้ว
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    private suspend fun ingestEvent(event: Event, session: WebSocketSession) {
        if (nip09.validDeletion(event)) {
            throw IllegalArgumentException("blocked: event rejected")
        }

        // เหตุการณ์ที่ลบได้ (kind 5 พร้อม e-tag) ต้องรู้สถานะ duplicate ก่อนจะรัน side effect ของการลบ
        // จึงยังคง SELECT เช็คแยกไว้ (ปริมาณ traffic ของเส้นทางนี้น้อยมากเทียบกับ event ทั่วไป)
        if (nip09.isDeletable(event)) {
            val existing: Event? = sqlExec.selectById(event.id!!)
            when {
                existing != null -> handleDuplicateEvent(event, session)
                else -> handleDeletableEvent(event, session)
            }
            return
        }

        handleNormalEvent(event, session)
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
        runCatching { action.invoke() }
            .onSuccess { (success, message) ->
                if (success) {
                    LOG.info("[EVENT] Saved id={} kind={} session={}", event.id, event.kind, session.id)
                    RelayResponse.OK(event.id!!, true, message).toClient(session)
                } else {
                    LOG.warn("[EVENT] Rejected id={} reason={}", event.id, message)
                    RelayResponse.OK(event.id!!, false, message).toClient(session)
                }
            }
            .onFailure { e ->
                LOG.error("[EVENT] Failed to handle id={}", event.id, e)
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
            when (sqlExec.saveIfAbsent(event)) {
                SaveOutcome.SAVED -> {
                    eventBus.publish(event)
                    true to ""
                }

                SaveOutcome.DUPLICATE -> false to "duplicate: already have this event"
                SaveOutcome.FAILED -> false to "error: could not save event to the database"
            }
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
                if (status) eventBus.publish(event)
                status to (if (status) message else "error: could not save event to the database after deletion")
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
        if (!status) {
            LOG.warn("[REQ] Invalid subscription={} reason={}", subscriptionId, warning)
            RelayResponse.NOTICE(warning).toClient(session)
            return
        }

        val context = PolicyContext(session, CommandType.REQ, filters = filtersX, subscriptionId = subscriptionId)
        when (val decision = policyController.evaluate(context)) {
            is PolicyDecision.Allow -> handleValidRequest(subscriptionId, filtersX, session)
            is PolicyDecision.Deny -> RelayResponse.CLOSED(subscriptionId, decision.reason).toClient(session)
            is PolicyDecision.RequireAuth -> RelayResponse.CLOSED(subscriptionId, decision.reason).toClient(session)
        }
    }


    private suspend fun handleValidRequest(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        session: WebSocketSession
    ) {
        val entry = SubscriptionEntry(session.id, subscriptionId, session, filtersX, CommandType.REQ)
        if (!registry.register(entry)) {
            RelayResponse.CANCEL("duplicate: $subscriptionId already opened").toClient(session)
            return
        }
        LOG.info("[REQ] Open subscription={} filters={} session={}", subscriptionId, filtersX.size, session.id)
        startConsumer(entry)

        filtersX.forEach { filter ->
            sqlExec.filterList(filter)?.forEach { event ->
                RelayResponse.EVENT(subscriptionId, event).toClient(session)
            }
        }
        RelayResponse.EOSE(subscriptionId).toClient(session)
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
        if (!status) {
            LOG.warn("[COUNT] Invalid subscription={} reason={}", subscriptionId, warning)
            RelayResponse.NOTICE(warning).toClient(session)
            return
        }

        val context = PolicyContext(session, CommandType.COUNT, filters = filtersX, subscriptionId = subscriptionId)
        when (val decision = policyController.evaluate(context)) {
            is PolicyDecision.Allow -> handleValidCount(subscriptionId, filtersX, session)
            is PolicyDecision.Deny -> RelayResponse.CLOSED(subscriptionId, decision.reason).toClient(session)
            is PolicyDecision.RequireAuth -> RelayResponse.CLOSED(subscriptionId, decision.reason).toClient(session)
        }
    }


    private suspend fun handleValidCount(
        subscriptionId: String,
        filtersX: List<FiltersX>,
        session: WebSocketSession
    ) {
        val entry = SubscriptionEntry(session.id, subscriptionId, session, filtersX, CommandType.COUNT)
        if (!registry.register(entry)) {
            RelayResponse.CANCEL("duplicate: $subscriptionId already opened").toClient(session)
            return
        }
        startConsumer(entry)

        var total = 0L
        filtersX.forEach { filter ->
            val count = sqlExec.filterList(filter)?.size ?: 0
            total += count

            val response = when {
                count < 0 -> throw IllegalArgumentException("Count cannot be negative")
                count >= 93_412_452 -> ApproximateCountREQ(93_412_452, true)
                else -> CountREQ(count)
            }

            RelayResponse.COUNT(subscriptionId, response).toClient(session)
        }
        entry.liveCount = total
        LOG.info("[COUNT] subscription={} count={}", subscriptionId, total)

        // แจ้งว่าเสร็จสิ้นการนับจำนวนเหตุการณ์
        RelayResponse.EOSE(subscriptionId).toClient(session)
    }


    /**
     * ฟังก์ชัน startConsumer ใช้ในการอ่าน events ที่ถูก push เข้ามาใน channel ของ subscription
     * แล้วส่งต่อไปยังไคลเอนต์ผ่าน WebSocket แทนที่จะ query ฐานข้อมูลซ้ำ
     *
     * @param entry subscription entry ที่ลงทะเบียนไว้ใน SubscriptionRegistry
     */
    private fun startConsumer(entry: SubscriptionEntry) {
        pushScope.launch {
            for (event in entry.channel) {
                if (!entry.session.isOpen) {
                    registry.unregister(entry.sessionId, entry.subscriptionId)
                    break
                }
                when (entry.kind) {
                    CommandType.REQ -> RelayResponse.EVENT(entry.subscriptionId, event).toClient(entry.session)
                    CommandType.COUNT -> {
                        entry.liveCount += 1
                        val data = if (entry.liveCount >= 93_412_452) ApproximateCountREQ(93_412_452, true)
                        else CountREQ(entry.liveCount.toInt())
                        RelayResponse.COUNT(entry.subscriptionId, data).toClient(entry.session)
                    }

                    else -> {}
                }
            }
        }
    }


    /**
     * ฟังก์ชัน onClose ใช้ในการจัดการคำขอปิดการเชื่อมต่อ WebSocket
     *
     * @param subscriptionId ไอดีที่ใช้ในการติดตามหรืออ้างอิงการร้องขอนั้นๆ จากไคลเอนต์
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    suspend fun onClose(subscriptionId: String, session: WebSocketSession) {
        // ไม่มีนโยบายใดปฏิเสธ CLOSE ในปัจจุบัน แต่ยังคงผ่าน policyController เพื่อให้ CLOSE เป็นส่วนหนึ่งของ
        // pipeline เดียวกันกับคำสั่งอื่น (เช่น เผื่ออนาคตอยากจำกัดอัตราการ CLOSE ถี่เกินไป) โดยไม่ต้องแก้ไฟล์นี้อีก
        policyController.evaluate(PolicyContext(session, CommandType.CLOSE, subscriptionId = subscriptionId))

        registry.unregister(session.id, subscriptionId)
        LOG.info("[CLOSE] Subscription closed id={} session={}", subscriptionId, session.id)
        RelayResponse.CANCEL(subscriptionId).toClient(session)
    }


    /**
     * ฟังก์ชัน onAuth ใช้ในการจัดการ AUTH event (kind 22242) ที่ไคลเอนต์ส่งมาเพื่อยืนยันตัวตนตาม NIP-42
     * @param event auth event ที่ไคลเอนต์เซ็นชื่อและส่งมา
     * @param status ผลตรวจสอบ NIP-01 (event id / signature / pubkey) ที่ CommandFactory ตรวจไว้แล้ว
     * @param warning ข้อความแจ้งเตือนกรณี NIP-01 ไม่ผ่าน (ถ้ามี)
     * @param session เซสชัน WebSocket ที่ส่ง AUTH event เข้ามา
     */
    suspend fun onAuth(event: Event, status: Boolean, warning: String, session: WebSocketSession) {
        if (!status) {
            LOG.warn("[AUTH] Invalid session={} reason={}", session.id, warning)
            RelayResponse.OK(event.id!!, false, warning).toClient(session)
        }

        // AUTH ต้องผ่าน policy pipeline เสมอ (ปัจจุบันอนุญาตเสมอ - ไม่มีการยืนยันตัวตนใดต้องผ่านก่อนจะ "เริ่ม" ยืนยันตัวตน)
        // ความถูกต้องของ auth event เอง (kind/challenge/relay tag) เป็นหน้าที่ของ nip42.verify ด้านล่าง ไม่ใช่ policy
        when (val decision = policyController.evaluate(PolicyContext(session, CommandType.AUTH, event = event))) {
            is PolicyDecision.Deny -> {
                RelayResponse.OK(event.id!!, false, decision.reason).toClient(session)
            }

            is PolicyDecision.RequireAuth -> {
                RelayResponse.OK(event.id!!, false, decision.reason).toClient(session)
            }

            is PolicyDecision.Allow -> Unit
        }

        val expectedChallenge = authentication.challengeFor(session)
        val (isValid, reason) = nip42.verify(event, expectedChallenge)

        if (isValid) {
            authentication.markAuthenticated(session, event.pubkey!!)
            LOG.info("[AUTH] Success pubkey={} session={}", event.pubkey, session.id)
            RelayResponse.OK(event.id!!, true, "").toClient(session)
        } else {
            LOG.warn("[AUTH] Failed pubkey={} reason={}", event.pubkey, reason)
            RelayResponse.OK(event.id!!, false, reason).toClient(session)
        }
    }


    /**
     * ฟังก์ชัน onUnknown ใช้ในการปิดการเชื่อมต่อ เพื่อจัดการคำสั่งที่ไม่รู้จักที่เข้ามาผ่าน WebSocket
     *
     * @param session เซสชัน WebSocket ที่ใช้ในการตอบกลับ
     */
    fun onUnknown(session: WebSocketSession) {
        LOG.warn("[COMMAND] Unknown command session={}", session.id)
        RelayResponse.NOTICE("Unknown command").toClient(session); session.close()
    }


    companion object {
        private val LOG = LoggerFactory.getLogger(BasicProtocolFlow::class.java)
    }

}