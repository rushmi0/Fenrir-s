package org.fenrirs.relay.core.policy

import io.micronaut.websocket.WebSocketSession
import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.models.Event
import org.fenrirs.relay.models.FiltersX
import org.slf4j.LoggerFactory

/**
 * ประเภทคำสั่งของ NIP-01 (และส่วนขยายอื่น ๆ) ที่ [PolicyController] รับผิดชอบตัดสินใจอนุญาต/ปฏิเสธ
 * เพิ่มคำสั่งใหม่ในอนาคตได้โดยเพิ่มค่าที่นี่ แล้วลงทะเบียน rule list ให้คำสั่งนั้นใน [PolicyController.evaluate]
 */
enum class CommandType { EVENT, REQ, COUNT, CLOSE, AUTH }

/**
 * Request object ที่ชั้น protocol (BasicProtocolFlow / Gateway) ส่งเข้ามาให้ [PolicyController] ประเมินผล
 * นี่คือ "ขอบเขต" เดียวใน policy subsystem ที่รู้จัก [WebSocketSession]
 *
 * @param session WebSocket session ของผู้ส่งคำสั่ง ใช้ resolve สถานะยืนยันตัวตนและ challenge เท่านั้น
 * @param command ประเภทคำสั่งที่กำลังประเมินผล
 * @param event event ที่แนบมากับคำสั่ง (EVENT, AUTH) ถ้ามี
 * @param filters เงื่อนไขที่แนบมากับคำสั่ง (REQ, COUNT) ถ้ามี
 * @param subscriptionId subscription id ที่แนบมากับคำสั่ง (REQ, COUNT, CLOSE) ถ้ามี
 */
data class PolicyContext(
    val session: WebSocketSession,
    val command: CommandType,
    val event: Event? = null,
    val filters: List<FiltersX> = emptyList(),
    val subscriptionId: String? = null
)

/**
 * ข้อมูลที่ [PolicyRule] ทุกตัวเห็นจริง ๆ ตอนประเมินผล ตั้งใจให้ "บริสุทธิ์" (pure/immutable, ไม่มี
 * WebSocketSession) เพื่อให้แต่ละ rule เป็น pure function จาก (config, RuleContext) -> PolicyDecision? ทดสอบได้
 * ตรง ๆ ด้วยการสร้าง object เปล่า ๆ โดยไม่ต้อง fake WebSocket - [PolicyController] คือผู้แปลง [PolicyContext]
 * (มี session) มาเป็น [RuleContext] นี้ หลังจาก resolve pubkey ที่ยืนยันตัวตนแล้วและ challenge จาก session
 */
data class RuleContext(
    val command: CommandType,
    val authenticatedPubkeys: Set<String> = emptySet(),
    val eventPubkey: String? = null,
    val challenge: String,
    val event: Event? = null,
    val filters: List<FiltersX> = emptyList(),
    val subscriptionId: String? = null
) {
    val isAuthenticated: Boolean get() = authenticatedPubkeys.isNotEmpty()

    /** true หาก pubkey ของ event ที่แนบมา หรือ pubkey ใด ๆ ที่ยืนยันตัวตนแล้วของ session นี้ อยู่ใน [pubkeys] */
    fun isAnyOf(pubkeys: Set<String>): Boolean =
        (eventPubkey != null && eventPubkey in pubkeys) || authenticatedPubkeys.any { it in pubkeys }
}

/**
 * ผลลัพธ์สุดท้ายที่ [PolicyController.evaluate] คืนกลับให้ชั้น protocol ใช้ตัดสินใจ ตั้งใจให้เหลือแค่ 3 เคสนี้
 * เพื่อให้ผู้เรียกใช้จบด้วย `when` แบบ exhaustive สั้น ๆ เสมอ:
 *
 * ```
 * when (val decision = policyController.evaluate(context)) {
 *     is PolicyDecision.Allow -> ...
 *     is PolicyDecision.Deny -> ... // decision.reason
 *     is PolicyDecision.RequireAuth -> ... // decision.challenge, decision.reason
 * }
 * ```
 */
sealed interface PolicyDecision {
    /** คำสั่งได้รับอนุญาตให้ดำเนินการต่อ */
    data object Allow : PolicyDecision

    /** คำสั่งถูกปฏิเสธถาวร (ไม่เกี่ยวกับการยืนยันตัวตน) พร้อมเหตุผลสำหรับ OK/CLOSED/NOTICE message */
    data class Deny(val reason: String) : PolicyDecision

    /**
     * คำสั่งถูกปฏิเสธเพราะ session ยังไม่ได้ยืนยันตัวตนตาม NIP-42
     * @param challenge challenge string ของ session นี้ (เผื่อ client ยังไม่เคยได้รับ หรือ retry)
     * @param reason ข้อความ (ขึ้นต้นด้วย "auth-required:" ตาม NIP-42) สำหรับใส่ใน OK/CLOSED message
     */
    data class RequireAuth(val challenge: String, val reason: String) : PolicyDecision
}

/**
 * สัญญาของ policy rule หนึ่งข้อ - ฟังก์ชันบริสุทธิ์เดียว จาก [RuleContext] ไปเป็น [PolicyDecision] หรือ `null`
 * หากกฎนี้ "ไม่มีความเห็น" สำหรับ context นี้ และอยากส่งไม้ต่อให้ rule ถัดไปตัดสินใจแทน
 *
 * เพิ่มกฎใหม่ = implement interface นี้ (ดู [WhitelistRule] เป็นตัวอย่าง) แล้วเพิ่มเข้า rule list ที่เหมาะสมใน
 * [PolicyController] เท่านั้น ไม่ต้องแก้ไข rule ที่มีอยู่เดิมเลย
 */
fun interface PolicyRule {
    suspend fun evaluate(context: RuleContext): PolicyDecision?
}

/**
 * หน้าตา (facade) เดียวที่ชั้น protocol (BasicProtocolFlow และคำสั่งใหม่ในอนาคต) คุยด้วย - เป็นจุดเดียวที่ต้อง
 * inject เพื่อขออนุญาตดำเนินการคำสั่งใด ๆ ไม่ว่าจะเป็น EVENT, REQ, COUNT, AUTH, CLOSE หรือคำสั่งใหม่
 *
 * ไล่ [PolicyRule] ทีละตัวตามลำดับในรายการที่เหมาะกับ [CommandType] นั้น หยุดที่ตัวแรกที่คืนค่าไม่ใช่ `null`
 * ถ้าไม่มีใครมีความเห็นเลยจะใช้ค่า default ของคำสั่งนั้น:
 *  - EVENT: deny-by-default เพราะรับ event เข้าฐานข้อมูลเป็นการกระทำที่มีผลถาวร ต้องมี rule รับรอง "Allow" ชัดเจน
 *  - REQ/COUNT/CLOSE/AUTH: allow-by-default เพราะเป็นการอ่านข้อมูลหรือคำสั่งควบคุม session ที่ไม่มีผลถาวร
 *
 * เพิ่ม/ลบ/สลับลำดับ rule ของคำสั่งหนึ่ง ๆ แก้ที่ [eventRules]/[readRules] เท่านั้น โดยไม่กระทบตัว rule เอง
 */
@Singleton
class PolicyController @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    whitelistRule: WhitelistRule,
    relayOwnerRule: RelayOwnerRule,
    authenticationRule: AuthenticationRule,
    passListRule: PassListRule,
    proofOfWorkRule: ProofOfWorkRule,
    featureAccessRule: FeatureAccessRule
) {

    // ลำดับตั้งใจ: ทางลัด (whitelist/owner) -> ประตู NIP-42 -> pass list -> Proof of Work เป็นด่านสุดท้าย
    private val eventRules: List<PolicyRule> =
        listOf(whitelistRule, relayOwnerRule, authenticationRule, passListRule, proofOfWorkRule)

    // REQ และ COUNT: ต้องผ่าน NIP-42 เมื่อ AUTH_ENABLED, จากนั้นต้องผ่าน feature permission ของ "feed" ด้วย
    private val readRules: List<PolicyRule> = listOf(whitelistRule, authenticationRule, featureAccessRule)

    // CLOSE และ AUTH ไม่มีเงื่อนไขใด ๆ - AUTH ต้องผ่านได้เสมอเพราะเป็นกลไกที่ใช้ "เข้าสู่" สถานะยืนยันตัวตนเอง
    // (การตรวจสอบความถูกต้องของ AUTH event ตาม NIP-42 เป็นหน้าที่ของ VerifyAuth แยกต่างหาก ไม่ใช่ policy)

    suspend fun evaluate(context: PolicyContext): PolicyDecision {
        val rules = when (context.command) {
            CommandType.EVENT -> eventRules
            CommandType.REQ, CommandType.COUNT -> readRules
            CommandType.CLOSE, CommandType.AUTH -> return PolicyDecision.Allow
        }

        val ruleContext = RuleContext(
            command = context.command,
            authenticatedPubkeys = authSessionStore.authenticatedPubkeys(context.session),
            eventPubkey = context.event?.pubkey,
            challenge = authSessionStore.challengeFor(context.session),
            event = context.event,
            filters = context.filters,
            subscriptionId = context.subscriptionId
        )

        for (rule in rules) {
            rule.evaluate(ruleContext)?.let { return it.logIfDenied(context) }
        }

        return if (context.command == CommandType.EVENT) {
            PolicyDecision.Deny("blocked: no permission").logIfDenied(context)
        } else {
            PolicyDecision.Allow
        }
    }

    private fun PolicyDecision.logIfDenied(context: PolicyContext): PolicyDecision {
        when (this) {
            is PolicyDecision.Deny -> LOG.warn("[POLICY] Access denied action={} reason={}", context.command, reason)
            is PolicyDecision.RequireAuth -> LOG.warn("[POLICY] Access denied action={} reason={}", context.command, reason)
            is PolicyDecision.Allow -> Unit
        }
        return this
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PolicyController::class.java)
    }
}