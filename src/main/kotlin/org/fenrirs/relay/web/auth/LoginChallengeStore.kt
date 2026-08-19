package org.fenrirs.relay.web.auth

import jakarta.inject.Singleton

import org.fenrirs.utils.ShiftTo.randomBytes
import org.fenrirs.utils.ShiftTo.toHex

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Single-use, short-lived challenges for the HTTP admin login handshake (see [AuthController] and
 * [org.fenrirs.relay.web.setup.SetupController]) - the HTTP counterpart of the challenge
 * [org.fenrirs.storage.Authentication] hands out per WebSocket session for relay-level NIP-42.
 */
@Singleton
class LoginChallengeStore {

    private val challenges = ConcurrentHashMap<String, Long>()

    fun issue(): String {
        val challenge = randomBytes(16).toHex()
        challenges[challenge] = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TTL_SECONDS
        return challenge
    }

    /** Consumes (single-use) the challenge if it exists and hasn't expired. */
    fun consume(challenge: String): Boolean {
        val expiresAt = challenges.remove(challenge) ?: return false
        return TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) <= expiresAt
    }

    companion object {
        private const val TTL_SECONDS = 5L * 60
    }
}
