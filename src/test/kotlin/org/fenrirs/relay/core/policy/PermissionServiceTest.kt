package org.fenrirs.relay.core.policy

import org.fenrirs.storage.service.AccountPermissionStore
import org.fenrirs.storage.service.OverrideState
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.RolePermission
import org.fenrirs.storage.service.RolePermissionStore

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class FakeRolePermissionStore : RolePermissionStore {
    val rows = mutableMapOf<Pair<PermissionRole, String>, Boolean>()

    override fun get(role: PermissionRole, featureId: String): Boolean? = rows[role to featureId]
    override fun allForRole(role: PermissionRole): Map<String, Boolean> =
        rows.filterKeys { it.first == role }.mapKeys { it.key.second }

    override fun all(): List<RolePermission> = rows.map { (k, v) -> RolePermission(k.first, k.second, v) }
    override fun set(role: PermissionRole, featureId: String, allowed: Boolean) {
        rows[role to featureId] = allowed
    }

    override fun setMany(role: PermissionRole, values: Map<String, Boolean>) {
        values.forEach { (featureId, allowed) -> set(role, featureId, allowed) }
    }
}

private class FakeAccountPermissionStore : AccountPermissionStore {
    val rows = mutableMapOf<Pair<String, String>, OverrideState>()

    override fun get(pubkey: String, featureId: String): OverrideState? = rows[pubkey to featureId]
    override fun allForAccount(pubkey: String): Map<String, OverrideState> =
        rows.filterKeys { it.first == pubkey }.mapKeys { it.key.second }

    override fun setOrClear(pubkey: String, featureId: String, state: OverrideState?) {
        if (state == null) rows.remove(pubkey to featureId) else rows[pubkey to featureId] = state
    }

    override fun setManyOrClear(pubkey: String, values: Map<String, OverrideState?>) {
        values.forEach { (featureId, state) -> setOrClear(pubkey, featureId, state) }
    }

    override fun removeAllForAccount(pubkey: String) {
        rows.keys.removeIf { it.first == pubkey }
    }
}

class PermissionServiceTest {

    private val rolePermissions = FakeRolePermissionStore()
    private val accountOverrides = FakeAccountPermissionStore()
    private val service = PermissionService(rolePermissions, accountOverrides)

    private val pubkey = "a".repeat(64)
    private val configurableFeature = "counter" // alwaysAdminOnly = false
    private val adminOnlyFeature = "role_management" // alwaysAdminOnly = true

    @Test
    fun `admin is always allowed regardless of table content`() {
        rolePermissions.set(PermissionRole.GENERAL, configurableFeature, false)
        rolePermissions.set(PermissionRole.GUEST, configurableFeature, false)
        accountOverrides.setOrClear(pubkey, configurableFeature, OverrideState.DENY)

        val admin = PermissionSubject(EffectiveRole.ADMIN, pubkey)
        assertTrue(service.canAccess(admin, configurableFeature))
        assertTrue(service.canAccess(admin, adminOnlyFeature))
    }

    @Test
    fun `general with no override follows the role default - allow`() {
        rolePermissions.set(PermissionRole.GENERAL, configurableFeature, true)
        val subject = PermissionSubject(EffectiveRole.GENERAL, pubkey)
        assertTrue(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `general with no override follows the role default - deny`() {
        rolePermissions.set(PermissionRole.GENERAL, configurableFeature, false)
        val subject = PermissionSubject(EffectiveRole.GENERAL, pubkey)
        assertFalse(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `general override ALLOW beats a role-default deny`() {
        rolePermissions.set(PermissionRole.GENERAL, configurableFeature, false)
        accountOverrides.setOrClear(pubkey, configurableFeature, OverrideState.ALLOW)
        val subject = PermissionSubject(EffectiveRole.GENERAL, pubkey)
        assertTrue(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `general override DENY beats a role-default allow`() {
        rolePermissions.set(PermissionRole.GENERAL, configurableFeature, true)
        accountOverrides.setOrClear(pubkey, configurableFeature, OverrideState.DENY)
        val subject = PermissionSubject(EffectiveRole.GENERAL, pubkey)
        assertFalse(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `clearing an override falls back to the role default`() {
        rolePermissions.set(PermissionRole.GENERAL, configurableFeature, true)
        accountOverrides.setOrClear(pubkey, configurableFeature, OverrideState.DENY)
        accountOverrides.setOrClear(pubkey, configurableFeature, null) // back to INHERIT

        val subject = PermissionSubject(EffectiveRole.GENERAL, pubkey)
        assertTrue(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `guest ignores account overrides entirely`() {
        rolePermissions.set(PermissionRole.GUEST, configurableFeature, true)
        accountOverrides.setOrClear(pubkey, configurableFeature, OverrideState.DENY) // irrelevant - guest has no pubkey

        val subject = PermissionSubject(EffectiveRole.GUEST, null)
        assertTrue(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `guest role-default deny`() {
        rolePermissions.set(PermissionRole.GUEST, configurableFeature, false)
        val subject = PermissionSubject(EffectiveRole.GUEST, null)
        assertFalse(service.canAccess(subject, configurableFeature))
    }

    @Test
    fun `unknown feature id fails closed`() {
        val subject = PermissionSubject(EffectiveRole.GENERAL, pubkey)
        assertFalse(service.canAccess(subject, "does_not_exist"))
    }

    @Test
    fun `always-admin-only feature denies general and guest even with a permissive role default row`() {
        rolePermissions.set(PermissionRole.GENERAL, adminOnlyFeature, true)
        rolePermissions.set(PermissionRole.GUEST, adminOnlyFeature, true)
        accountOverrides.setOrClear(pubkey, adminOnlyFeature, OverrideState.ALLOW)

        assertFalse(service.canAccess(PermissionSubject(EffectiveRole.GENERAL, pubkey), adminOnlyFeature))
        assertFalse(service.canAccess(PermissionSubject(EffectiveRole.GUEST, null), adminOnlyFeature))
    }

    @Test
    fun `resolveSubject maps operator auth roles to the right effective role`() {
        assertTrue(service.resolveSubject("OWNER", pubkey).effectiveRole == EffectiveRole.ADMIN)
        assertTrue(service.resolveSubject("ADMIN", pubkey).effectiveRole == EffectiveRole.ADMIN)
        assertTrue(service.resolveSubject("OPERATOR", pubkey).effectiveRole == EffectiveRole.GENERAL)
        assertTrue(service.resolveSubject(null, null).effectiveRole == EffectiveRole.GUEST)
        assertTrue(service.resolveSubject("garbage", pubkey).effectiveRole == EffectiveRole.GUEST)
    }
}
