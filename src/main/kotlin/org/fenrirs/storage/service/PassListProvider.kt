package org.fenrirs.storage.service


fun interface PassListProvider {
    suspend fun followedPubkeys(ownerPubkey: String): Set<String>
}