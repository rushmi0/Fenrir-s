package org.fenrirs.stored

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.utils.Bech32
import org.fenrirs.utils.ShiftTo.toHex
import org.fenrirs.relay.policy.NostrRelayConfig

@Bean
@Context
@Singleton
class Environment @Inject constructor(private val config: NostrRelayConfig) {

    private val dotenv: Dotenv = dotenv {
        directory = "." // root path
        filename = ".env"
    }

    // Database settings
    val DATABASE_NAME: String by lazy { dotenv["DATABASE_NAME"] }
    val DATABASE_URL: String by lazy { dotenv["DATABASE_URL"] }
    val DATABASE_USERNAME: String by lazy { dotenv["DATABASE_USERNAME"] }
    val DATABASE_PASSWORD: String by lazy { dotenv["DATABASE_PASSWORD"] }

    // Relay info
    val RELAY_OWNER: String by lazy { if (config.info.npub.startsWith("npub")) Bech32.decode(config.info.npub).data.toHex() else config.info.npub }
    val RELAY_NAME: String by lazy { config.info.name }
    val RELAY_DESCRIPTION: String by lazy { config.info.description }
    val RELAY_CONTACT: String by lazy { config.info.contact }

    // Policy settings
    val FOLLOWS_PASS: Boolean by lazy { config.policy.followsPass }
    val PROOF_OF_WORK_ENABLED: Boolean by lazy { config.policy.proofOfWork.enabled }
    val PROOF_OF_WORK_DIFFICULTY: Int by lazy { config.policy.proofOfWork.difficultyMinimum }
    val ALL_PASS: Boolean by lazy { config.policy.allPass }

    // Limitation settings
    val MAX_FILTERS: Int = 10
    val MAX_LIMIT: Int = 150
    val PAYMENT_REQ: Boolean = false
    val AUTH_REQ: Boolean = false

    // Database backup settings
    val BACKUP_ENABLED: Boolean by lazy { config.database.backup.enabled }
    val BACKUP_SYNC: List<String> by lazy { config.database.backup.sync }

}
