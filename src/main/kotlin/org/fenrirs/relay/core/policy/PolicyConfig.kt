package org.fenrirs.relay.core.policy

import org.fenrirs.storage.service.PermissionRole
import org.fenrirs.storage.service.WhitelistStatus
import org.fenrirs.storage.statement.WhitelistAccountStoreImpl

interface PolicyConfig {

    val AUTH_ENABLED: Boolean

    val AUTH_WHITELIST_PUBKEYS: Set<String>

    val RELAY_OWNER: String

    val ALL_PASS: Boolean

    val FOLLOWS_PASS: Boolean

    val PROOF_OF_WORK_ENABLED: Boolean

    /**
     * True when [pubkeyHex] should resolve to the General permission tier purely by virtue of
     * being an ACTIVE, GENERAL-role entry in [org.fenrirs.storage.table.WHITELIST_ACCOUNT] - the
     * only way (besides being the relay's own operator row) a pubkey ever reaches General now;
     * see [org.fenrirs.relay.core.policy.PermissionService.resolveByPubkey]. Gated on
     * [AUTH_ENABLED]: a whitelist entry is inert while NIP-42 auth itself is switched off,
     * matching "when I set it to enter the Whitelist (Auth Enabled), it will be General."
     */
    fun isGeneralWhitelisted(pubkeyHex: String): Boolean {
        if (!AUTH_ENABLED) return false
        val account = WhitelistAccountStoreImpl.find(pubkeyHex) ?: return false
        return account.role == PermissionRole.GENERAL && account.status == WhitelistStatus.ACTIVE
    }
}