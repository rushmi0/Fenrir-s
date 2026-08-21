package org.fenrirs.relay.core.policy

import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.statement.RolePermissionStoreImpl

/**
 * Idempotent "insert only what's missing" seeding, same pattern as
 * [org.fenrirs.storage.statement.KeyValueStoreImpl.sync]. Runs on every boot (not just first boot)
 * so appending a new [Feature] to [FeatureRegistry] automatically gets a correctly-defaulted
 * GENERAL/GUEST row on the next restart, with no manual migration step - an Admin's later toggle
 * always takes precedence over these seed values since [seed] never overwrites an existing row.
 */
object PermissionSeeder {

    fun seed() {
        val existingGeneral = RolePermissionStoreImpl.allForRole(PermissionRole.GENERAL).keys
        val existingGuest = RolePermissionStoreImpl.allForRole(PermissionRole.GUEST).keys

        FeatureRegistry.all().forEach { feature ->
            if (feature.id !in existingGeneral) {
                RolePermissionStoreImpl.set(PermissionRole.GENERAL, feature.id, feature.defaultGeneralAllowed)
            }
            if (feature.id !in existingGuest) {
                RolePermissionStoreImpl.set(PermissionRole.GUEST, feature.id, feature.defaultGuestAllowed)
            }
        }
    }
}
