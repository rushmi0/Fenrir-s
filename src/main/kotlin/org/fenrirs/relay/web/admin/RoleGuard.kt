package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.hateoas.JsonError

/**
 * Roles supported in Phase 1 (Relay Owner / Admin / Operator only - see task scope). Ordered by
 * privilege level so [RoleGuard] can do simple `>=` comparisons instead of a permission matrix.
 */
enum class Role(val level: Int) {
    OPERATOR(0),
    ADMIN(1),
    OWNER(2);

    companion object {
        fun from(value: String?): Role? = entries.firstOrNull { it.name == value }
    }
}

/**
 * Authorization checks for the Admin API, kept deliberately separate from [org.fenrirs.relay.web.AdminAuthFilter]
 * (authentication - "who is this token for") so each concern stays a single-responsibility unit.
 * OPERATOR is read-only everywhere; ADMIN and OWNER can read/write config and policy; only OWNER
 * can manage other operators.
 */
object RoleGuard {

    fun canWrite(role: String?): Boolean = (Role.from(role)?.level ?: -1) >= Role.ADMIN.level

    fun isOwner(role: String?): Boolean = Role.from(role) == Role.OWNER

    fun forbidden(message: String = "insufficient role - ADMIN or OWNER required"): HttpResponse<*> =
        HttpResponse.status<JsonError>(HttpStatus.FORBIDDEN).body(JsonError(message))
}
