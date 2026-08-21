package org.fenrirs.storage.statement

import jakarta.inject.Singleton

import org.fenrirs.storage.DatabaseFactory.configTask
import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.RolePermission
import org.fenrirs.storage.service.RolePermissionStore
import org.fenrirs.storage.table.ROLE_PERMISSION

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

@Singleton
object RolePermissionStoreImpl : RolePermissionStore {

    override fun get(role: PermissionRole, featureId: String): Boolean? = configTask {
        ROLE_PERMISSION.selectAll()
            .where { (ROLE_PERMISSION.ROLE eq role.name) and (ROLE_PERMISSION.FEATURE_ID eq featureId) }
            .firstOrNull()?.get(ROLE_PERMISSION.ALLOWED)
    }

    override fun allForRole(role: PermissionRole): Map<String, Boolean> = configTask {
        ROLE_PERMISSION.selectAll()
            .where { ROLE_PERMISSION.ROLE eq role.name }
            .associate { it[ROLE_PERMISSION.FEATURE_ID] to it[ROLE_PERMISSION.ALLOWED] }
    }

    override fun all(): List<RolePermission> = configTask {
        ROLE_PERMISSION.selectAll().map { it.toRolePermission() }
    }

    override fun set(role: PermissionRole, featureId: String, allowed: Boolean) {
        configTask {
            ROLE_PERMISSION.upsert {
                it[ROLE] = role.name
                it[FEATURE_ID] = featureId
                it[ALLOWED] = allowed
            }
        }
    }

    override fun setMany(role: PermissionRole, values: Map<String, Boolean>) {
        configTask {
            values.forEach { (featureId, allowed) ->
                ROLE_PERMISSION.upsert {
                    it[ROLE] = role.name
                    it[FEATURE_ID] = featureId
                    it[ALLOWED] = allowed
                }
            }
        }
    }

    private fun ResultRow.toRolePermission() = RolePermission(
        role = PermissionRole.valueOf(this[ROLE_PERMISSION.ROLE]),
        featureId = this[ROLE_PERMISSION.FEATURE_ID],
        allowed = this[ROLE_PERMISSION.ALLOWED]
    )
}
