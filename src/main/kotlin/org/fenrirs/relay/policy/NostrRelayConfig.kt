package org.fenrirs.relay.policy

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("nostr.relay")
class NostrRelayConfig {
    lateinit var info: Info
    lateinit var policy: Policy
    lateinit var profile: Profile

    @ConfigurationProperties("info")
    class Info {
        lateinit var name: String
        lateinit var description: String
        lateinit var npub: String
        lateinit var contact: String
    }

    @ConfigurationProperties("policy")
    class Policy {
        lateinit var follows: Follows
        lateinit var proofOfWork: ProofOfWork

        @ConfigurationProperties("follows")
        class Follows {
            var pass: Boolean = false
        }

        @ConfigurationProperties("proof_of_work")
        class ProofOfWork {
            var enabled: Boolean = false
            var difficultyMinimum: Int = 0
        }
    }

    @ConfigurationProperties("profile")
    class Profile {
        lateinit var backup: Backup

        @ConfigurationProperties("backup")
        class Backup {
            var enabled: Boolean = false
            lateinit var sync: List<String>
        }
    }
}
