package org.fenrirs.storage.statement

import jakarta.inject.Singleton

import org.fenrirs.storage.DatabaseFactory.configTask
import org.fenrirs.storage.service.AccountPermissionStore
import org.fenrirs.storage.service.OverrideState
import org.fenrirs.storage.table.ACCOUNT_PERMISSION_OVERRIDE

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

@Singleton
object AccountPermissionStoreImpl : AccountPermissionStore {

    override fun get(pubkey: String, featureId: String): OverrideState? = configTask {
        ACCOUNT_PERMISSION_OVERRIDE.selectAll()
            .where { (ACCOUNT_PERMISSION_OVERRIDE.PUBKEY eq pubkey) and (ACCOUNT_PERMISSION_OVERRIDE.FEATURE_ID eq featureId) }
            .firstOrNull()?.let { OverrideState.valueOf(it[ACCOUNT_PERMISSION_OVERRIDE.STATE]) }
    }

    override fun allForAccount(pubkey: String): Map<String, OverrideState> = configTask {
        ACCOUNT_PERMISSION_OVERRIDE.selectAll()
            .where { ACCOUNT_PERMISSION_OVERRIDE.PUBKEY eq pubkey }
            .associate { it[ACCOUNT_PERMISSION_OVERRIDE.FEATURE_ID] to OverrideState.valueOf(it[ACCOUNT_PERMISSION_OVERRIDE.STATE]) }
    }

    override fun setOrClear(pubkey: String, featureId: String, state: OverrideState?) {
        configTask {
            if (state == null) {
                ACCOUNT_PERMISSION_OVERRIDE.deleteWhere {
                    (PUBKEY eq pubkey) and (FEATURE_ID eq featureId)
                }
            } else {
                ACCOUNT_PERMISSION_OVERRIDE.upsert {
                    it[PUBKEY] = pubkey
                    it[FEATURE_ID] = featureId
                    it[STATE] = state.name
                    it[UPDATED_AT] = System.currentTimeMillis() / 1000
                }
            }
        }
    }

    override fun setManyOrClear(pubkey: String, values: Map<String, OverrideState?>) {
        values.forEach { (featureId, state) -> setOrClear(pubkey, featureId, state) }
    }

    override fun removeAllForAccount(pubkey: String) {
        configTask {
            ACCOUNT_PERMISSION_OVERRIDE.deleteWhere { PUBKEY eq pubkey }
        }
    }
}
