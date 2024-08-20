package org.fenrirs.relay.policy

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import jakarta.inject.Singleton

@Context
@Singleton
@ConfigurationProperties("nostr.relay")
class NostrRelayConfig {
    lateinit var info: Info
    lateinit var policy: Policy
    lateinit var database: Database

    @ConfigurationProperties("info")
    class Info {
        lateinit var name: String
        lateinit var description: String
        lateinit var npub: String
        lateinit var contact: String
    }

    @ConfigurationProperties("policy")
    class Policy {

        lateinit var proofOfWork: ProofOfWork
        var followsPass: Boolean = false
        var allPass: Boolean = false

        @ConfigurationProperties("proof_of_work")
        class ProofOfWork {
            var enabled: Boolean = false
            var difficultyMinimum: Int = 0
        }
    }

    @ConfigurationProperties("database")
    class Database {
        lateinit var backup: Backup

        @ConfigurationProperties("backup")
        class Backup {
            var enabled: Boolean = false
            lateinit var sync: List<String>
        }
    }
}
