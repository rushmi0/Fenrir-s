package org.fenrirs.relay.core.policy

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.web.admin.Role
import org.fenrirs.storage.service.AccountPermissionStore
import org.fenrirs.storage.service.OperatorStore
import org.fenrirs.storage.service.OverrideState
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.RolePermissionStore
import org.fenrirs.storage.statement.AccountPermissionStoreImpl
import org.fenrirs.storage.statement.OperatorStoreImpl
import org.fenrirs.storage.statement.RolePermissionStoreImpl


enum class EffectiveRole { ADMIN, GENERAL, GUEST }

data class PermissionSubject(val effectiveRole: EffectiveRole, val pubkey: String?)

@Singleton
class PermissionService(
    private val rolePermissions: RolePermissionStore,
    private val accountOverrides: AccountPermissionStore,
    private val operators: OperatorStore,
    private val policyConfig: PolicyConfig
) {

    // RolePermissionStoreImpl/AccountPermissionStoreImpl/OperatorStoreImpl are Kotlin `object`s,
    // which Micronaut can't constructor-inject as their interface types - hardcoded here instead.
    // PolicyConfig (backed by NostrRelayConfig, a real bean) is injected normally.
    @Inject constructor(policyConfig: PolicyConfig) : this(
        RolePermissionStoreImpl, AccountPermissionStoreImpl, OperatorStoreImpl, policyConfig
    )


    fun resolveSubject(authRole: String?, pubkey: String?): PermissionSubject {
        val role = Role.from(authRole)
        return when {
            role != null && role.level >= Role.ADMIN.level -> PermissionSubject(EffectiveRole.ADMIN, pubkey)
            role == Role.OPERATOR -> PermissionSubject(EffectiveRole.GENERAL, pubkey)
            else -> PermissionSubject(EffectiveRole.GUEST, null)
        }
    }

    /**
     * Resolves a subject purely from a pubkey, with no pre-resolved role string to hand it (e.g. a
     * NIP-42-authenticated WebSocket connection, or an Admin Console login attempt that hasn't
     * been assigned a session yet) - checks the operator table first (the relay's own OWNER, plus
     * any legacy ADMIN/OPERATOR rows granted before operator self-service was locked down), then
     * falls back to the auth whitelist for General, then Guest. Single source of truth for "who
     * counts as General now" so [org.fenrirs.relay.web.auth.AuthController]'s login gate and
     * [FeatureAccessRule]'s WebSocket-side REQ/COUNT gate can never diverge on the answer.
     */
    fun resolveByPubkey(pubkey: String?): PermissionSubject {
        val operatorRole = pubkey?.let { operators.find(it)?.role }
        if (operatorRole != null) return resolveSubject(operatorRole, pubkey)
        if (pubkey != null && policyConfig.isGeneralWhitelisted(pubkey)) {
            return PermissionSubject(EffectiveRole.GENERAL, pubkey)
        }
        return PermissionSubject(EffectiveRole.GUEST, null)
    }


    fun canAccess(subject: PermissionSubject, featureId: String): Boolean {
        val feature = FeatureRegistry.find(featureId) ?: return false

        if (subject.effectiveRole == EffectiveRole.ADMIN) return true
        if (feature.alwaysAdminOnly) return false

        return when (subject.effectiveRole) {
            EffectiveRole.GENERAL -> {
                val override = subject.pubkey?.let { accountOverrides.get(it, featureId) }
                when (override) {
                    OverrideState.ALLOW -> true
                    OverrideState.DENY -> false
                    null -> rolePermissions.get(PermissionRole.GENERAL, featureId) ?: feature.defaultGeneralAllowed
                }
            }

            EffectiveRole.GUEST ->
                rolePermissions.get(PermissionRole.GUEST, featureId) ?: feature.defaultGuestAllowed

            EffectiveRole.ADMIN -> true // unreachable, handled above
        }
    }

    fun effectivePermissions(subject: PermissionSubject): Map<String, Boolean> =
        FeatureRegistry.all().associate { it.id to canAccess(subject, it.id) }
}
