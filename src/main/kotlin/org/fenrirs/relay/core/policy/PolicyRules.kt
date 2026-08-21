package org.fenrirs.relay.core.policy

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.core.nip.nip13.ProofOfWork
import org.fenrirs.storage.service.OperatorStore
import org.fenrirs.storage.service.PassListProvider
import org.fenrirs.storage.statement.OperatorStoreImpl



/**
 * NIP-42 whitelist bypass: pubkey ที่อยู่ใน `AUTH_WHITELIST_PUBKEYS` (relay operator, bot, internal service ฯลฯ)
 * ข้าม rule ที่เหลือทั้งหมดไปเลยทันที ไม่ว่า `AUTH_ENABLED` จะเป็น true หรือไม่ก็ตาม และไม่ว่าจะเป็นคำสั่งใด
 * ตั้งใจให้เป็น rule แรก ๆ เสมอ (ดู [PolicyController]) เพื่อให้เป็นทางลัดที่ชัดเจนที่สุด
 */
@Singleton
class WhitelistRule @Inject constructor(private val config: PolicyConfig) : PolicyRule {

    override suspend fun evaluate(context: RuleContext): PolicyDecision? =
        if (context.isAnyOf(config.AUTH_WHITELIST_PUBKEYS)) PolicyDecision.Allow else null
}

/**
 * เจ้าของ relay (`RELAY_OWNER`, ตั้งค่าจาก NPUB) เผยแพร่ event ได้เสมอ ไม่ว่านโยบาย pass list / Proof of Work
 * จะเข้มงวดแค่ไหนก็ตาม
 */
@Singleton
class RelayOwnerRule @Inject constructor(private val config: PolicyConfig) : PolicyRule {

    override suspend fun evaluate(context: RuleContext): PolicyDecision? {
        val owner = config.RELAY_OWNER
        return if (owner.isNotBlank() && context.eventPubkey == owner) PolicyDecision.Allow else null
    }
}

/**
 * แกนหลักของ NIP-42 ("Authentication of clients to relays"): เมื่อ `AUTH_ENABLED=true` แล้ว session ที่ยังไม่ผ่าน
 * การยืนยันตัวตนต้องได้รับ [PolicyDecision.RequireAuth] กลับไปเสมอ - ชั้น protocol (BasicProtocolFlow) มีหน้าที่
 * แปลงผลลัพธ์นี้เป็น response ตาม spec ("auth-required: " prefix ใน OK message สำหรับ EVENT หรือ CLOSED message
 * สำหรับ REQ/COUNT)
 */
@Singleton
class AuthenticationRule @Inject constructor(private val config: PolicyConfig) : PolicyRule {

    override suspend fun evaluate(context: RuleContext): PolicyDecision? {
        if (!config.AUTH_ENABLED || context.isAuthenticated) return null
        return PolicyDecision.RequireAuth(context.challenge, DEFAULT_REASON)
    }

    companion object {
        const val DEFAULT_REASON = "auth-required: this relay requires authentication"
    }
}

/**
 * ทำหน้าที่แทนตรรกะ `ALL_PASS` / `FOLLOWS_PASS`:
 *  - `ALL_PASS=true` และ `FOLLOWS_PASS=false` -> เปิดกว้าง ใครก็เผยแพร่ event ได้โดยไม่ต้องทำ Proof of Work
 *  - `FOLLOWS_PASS=true` และ pubkey ของ event อยู่ในรายชื่อที่เจ้าของ relay ติดตาม (web of trust) -> ผ่านเช่นกัน
 *  - นอกเหนือจากนั้น -> `null` ส่งต่อให้ [ProofOfWorkRule] ตัดสินใจแทน
 */
@Singleton
class PassListRule @Inject constructor(
    private val config: PolicyConfig,
    private val passListProvider: PassListProvider
) : PolicyRule {

    override suspend fun evaluate(context: RuleContext): PolicyDecision? {
        val pubkey = context.eventPubkey ?: return null

        if (config.ALL_PASS && !config.FOLLOWS_PASS) return PolicyDecision.Allow

        if (config.FOLLOWS_PASS && pubkey in passListProvider.followedPubkeys(config.RELAY_OWNER)) {
            return PolicyDecision.Allow
        }

        return null
    }
}

/**
 * NIP-13 gate สำหรับ event ที่ไม่ได้ผ่านทาง [WhitelistRule], [RelayOwnerRule] หรือ [PassListRule] มาก่อน:
 * เมื่อ `POW_ENABLED=true` event ต้องมี tag `nonce` และมี difficulty ผ่านเกณฑ์ `MIN_DIFFICULTY` ที่ตั้งไว้
 * ถึงจะ [PolicyDecision.Allow] ไม่เช่นนั้น [PolicyDecision.Deny] ทันที เมื่อ `POW_ENABLED=false` -> `null` เสมอ
 * ปล่อยให้ default ของ EVENT pipeline (deny-by-default) ที่ [PolicyController] เป็นผู้ตัดสินขั้นสุดท้าย
 */
@Singleton
class ProofOfWorkRule @Inject constructor(
    private val config: PolicyConfig,
    private val nip13: ProofOfWork
) : PolicyRule {

    override suspend fun evaluate(context: RuleContext): PolicyDecision? {
        val event = context.event ?: return null
        if (!config.PROOF_OF_WORK_ENABLED) return null

        if (!nip13.isProofOfWorkEvent(event)) {
            return PolicyDecision.Deny("pow: event does not contain a valid \"nonce\" commitment")
        }

        val (valid, reason) = nip13.verifyProofOfWork(event, config.PROOF_OF_WORK_ENABLED)
        return if (valid) PolicyDecision.Allow else PolicyDecision.Deny(reason)
    }
}

/**
 * Backend-independent enforcement of the "feed" feature for REQ/COUNT: a WebSocket connection has
 * no bearer-token admin session, only whatever pubkey NIP-42 has proven for it (if any), so identity
 * here is resolved by looking that pubkey up in [OperatorStore] rather than reading a request
 * attribute. Anonymous connections (no proven pubkey) are Guest by definition. Delegates to the same
 * [PermissionService] the REST admin endpoints use - one evaluation mechanism, not a parallel one.
 */
@Singleton
class FeatureAccessRule(
    private val permissionService: PermissionService,
    private val operators: OperatorStore
) : PolicyRule {

    // Same Kotlin-object-via-DI pitfall as PermissionService's secondary constructor - see there.
    @Inject constructor(permissionService: PermissionService) : this(permissionService, OperatorStoreImpl)

    override suspend fun evaluate(context: RuleContext): PolicyDecision? {
        if (context.command != CommandType.REQ && context.command != CommandType.COUNT) return null

        val pubkey = context.authenticatedPubkeys.firstOrNull()
        val operatorRole = pubkey?.let { operators.find(it)?.role }
        val subject = permissionService.resolveSubject(operatorRole, pubkey)

        return if (permissionService.canAccess(subject, "feed")) null
        else PolicyDecision.Deny("blocked: feed access is not permitted for this account")
    }
}