package org.fenrirs.storage

import jakarta.inject.Singleton

import org.fenrirs.relay.core.policy.AdminSession
import org.fenrirs.relay.core.policy.AdminSessionStore
import org.fenrirs.utils.ShiftTo.randomBytes
import org.fenrirs.utils.ShiftTo.toHex

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Singleton
class AdminAuthentication : AdminSessionStore {

    private val sessions = ConcurrentHashMap<String, AdminSession>()

    override fun issue(pubkey: String, role: String): String {
        val token = randomBytes(32).toHex()
        val expiresAt = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + SESSION_TTL_SECONDS
        sessions[token] = AdminSession(pubkey, role, expiresAt)
        return token
    }

    override fun resolve(token: String): AdminSession? {
        val session = sessions[token] ?: return null
        val now = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
        if (now > session.expiresAt) {
            sessions.remove(token)
            return null
        }
        return session
    }

    override fun revoke(token: String) {
        sessions.remove(token)
    }

    override fun revokeAll(pubkey: String) {
        sessions.entries.removeIf { it.value.pubkey == pubkey }
    }

    companion object {
        private const val SESSION_TTL_SECONDS = 24L * 60 * 60
    }
}
