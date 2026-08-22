package org.fenrirs.relay.core.policy

/**
 * NAVIGATION = a top-level nav entry in the client shell (feed, counter, the console itself).
 * CONSOLE_TAB = one of the tabs rendered inside the Admin Console once `admin_console` is reachable.
 */
enum class FeatureCategory { NAVIGATION, CONSOLE_TAB }

/**
 * A single, stable, self-describing unit the permission system can be configured against.
 *
 * @param id stable snake_case identifier - never rename once shipped, it's a persisted key in
 *   [org.fenrirs.storage.table.ROLE_PERMISSION]/[org.fenrirs.storage.table.ACCOUNT_PERMISSION_OVERRIDE].
 * @param alwaysAdminOnly true means [PermissionService] short-circuits General/Guest to denied for
 *   this feature no matter what the role-permission table says - used for the existing Admin
 *   Console tabs (Relay Info/Policy/Database) and Role Management itself, which stay hard-locked
 *   to Admin so a General account can never be granted a path to escalate its own permissions.
 * @param defaultGeneralAllowed / [defaultGuestAllowed] seed values [PermissionSeeder] writes the
 *   first time this feature is seen - only takes effect once, an Admin's later toggle always wins.
 */
data class Feature(
    val id: String,
    val name: String,
    val description: String,
    val category: FeatureCategory,
    val alwaysAdminOnly: Boolean,
    val defaultGeneralAllowed: Boolean,
    val defaultGuestAllowed: Boolean
)

/**
 * Single source of truth for every feature the permission system knows about. Adding a feature
 * later = append one entry here (plus whatever REST/WebSocket call site should invoke
 * [PermissionService.canAccess] for it) - the Role Management UI, seeding, and every enforcement
 * point read from this list, nothing else needs to change.
 */
object FeatureRegistry {

    private val features: List<Feature> = listOf(
        Feature(
            id = "feed",
            name = "Feed",
            description = "Read the relay's public note feed (NIP-01 REQ/COUNT)",
            category = FeatureCategory.NAVIGATION,
            alwaysAdminOnly = false,
            defaultGeneralAllowed = true,
            defaultGuestAllowed = true
        ),
        Feature(
            id = "counter",
            name = "Counter",
            description = "Client-side counter utility page",
            category = FeatureCategory.NAVIGATION,
            alwaysAdminOnly = false,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "accounts",
            name = "Accounts",
            description = "Directory of relay users, sourced from kind-0 metadata events",
            category = FeatureCategory.NAVIGATION,
            alwaysAdminOnly = false,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "stats",
            name = "Dashboard",
            description = "Event/kind distribution, daily activity, and relay vitals",
            category = FeatureCategory.NAVIGATION,
            alwaysAdminOnly = true,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "admin_console",
            name = "Admin Console",
            description = "Open the Admin Console shell",
            category = FeatureCategory.NAVIGATION,
            alwaysAdminOnly = true,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "relay_info",
            name = "Relay Info",
            description = "View/edit relay identity and NIP-11 metadata",
            category = FeatureCategory.CONSOLE_TAB,
            alwaysAdminOnly = true,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "policy",
            name = "Policy",
            description = "View/edit relay security policy (AUTH/PoW/pass lists)",
            category = FeatureCategory.CONSOLE_TAB,
            alwaysAdminOnly = true,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "database",
            name = "Database",
            description = "View/edit database connection and pool settings",
            category = FeatureCategory.CONSOLE_TAB,
            alwaysAdminOnly = true,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        ),
        Feature(
            id = "role_management",
            name = "Role Management",
            description = "Manage roles, feature permissions, and account overrides",
            category = FeatureCategory.CONSOLE_TAB,
            alwaysAdminOnly = true,
            defaultGeneralAllowed = false,
            defaultGuestAllowed = false
        )
    )

    fun all(): List<Feature> = features

    fun find(id: String): Feature? = features.firstOrNull { it.id == id }

    fun byCategory(category: FeatureCategory): List<Feature> = features.filter { it.category == category }
}
