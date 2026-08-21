package org.fenrirs.storage.service

/** The two permission-table-backed roles - ADMIN is deliberately excluded, it's never persisted or queried. */
enum class PermissionRole { GENERAL, GUEST }

data class RolePermission(val role: PermissionRole, val featureId: String, val allowed: Boolean)

interface RolePermissionStore {

    /** null = no row yet for this (role, featureId) pair - caller should fall back to the feature's registry default. */
    fun get(role: PermissionRole, featureId: String): Boolean?

    fun allForRole(role: PermissionRole): Map<String, Boolean>

    fun all(): List<RolePermission>

    fun set(role: PermissionRole, featureId: String, allowed: Boolean)

    fun setMany(role: PermissionRole, values: Map<String, Boolean>)
}
