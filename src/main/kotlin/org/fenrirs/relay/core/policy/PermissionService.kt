package org.fenrirs.relay.core.policy

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.relay.web.admin.Role
import org.fenrirs.storage.service.AccountPermissionStore
import org.fenrirs.storage.service.OverrideState
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.RolePermissionStore
import org.fenrirs.storage.statement.AccountPermissionStoreImpl
import org.fenrirs.storage.statement.RolePermissionStoreImpl

/** The three product-level roles this whole system evaluates against - not to be confused with
 * [Role] (the underlying OPERATOR/ADMIN/OWNER auth tier), which this class translates from. */
enum class EffectiveRole { ADMIN, GENERAL, GUEST }

/**
 * @param pubkey non-null for GENERAL (and, incidentally, ADMIN); always null for GUEST, since a
 *   Guest by definition has no registered identity to look an override up against.
 */
data class PermissionSubject(val effectiveRole: EffectiveRole, val pubkey: String?)

/**
 * The single centralized permission-evaluation mechanism - every REST controller and the
 * WebSocket read path ([PolicyRules]'s `FeatureAccessRule`) must go through [canAccess] rather
 * than re-implementing role/override precedence locally.
 */
@Singleton
class PermissionService(
    private val rolePermissions: RolePermissionStore,
    private val accountOverrides: AccountPermissionStore
) {

    // Micronaut can't construct a bean whose target is a Kotlin `object` (its synthesized
    // constructor is JVM-private) when injected via an interface type - this codebase has always
    // sidestepped that by referencing such singletons (OperatorStoreImpl, KeyValueStoreImpl, ...)
    // directly rather than through DI. This secondary constructor is the one Micronaut actually
    // uses (see @Inject below); the primary constructor above stays available for direct,
    // container-free construction in tests with fake stores.
    @Inject constructor() : this(RolePermissionStoreImpl, AccountPermissionStoreImpl)

    /**
     * Translates the existing OPERATOR/ADMIN/OWNER auth-tier string (as already threaded through
     * [org.fenrirs.relay.web.AdminAuthFilter]/[Role]) into the product-level role this system
     * understands. `authRole = null` (no session / no registered operator) always resolves to GUEST.
     */
    fun resolveSubject(authRole: String?, pubkey: String?): PermissionSubject {
        val role = Role.from(authRole)
        return when {
            role != null && role.level >= Role.ADMIN.level -> PermissionSubject(EffectiveRole.ADMIN, pubkey)
            role == Role.OPERATOR -> PermissionSubject(EffectiveRole.GENERAL, pubkey)
            else -> PermissionSubject(EffectiveRole.GUEST, null)
        }
    }

    /**
     * Evaluation priority: Admin always allowed -> account override (GENERAL only) -> role default
     * -> deny. Unknown feature ids fail closed.
     */
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
