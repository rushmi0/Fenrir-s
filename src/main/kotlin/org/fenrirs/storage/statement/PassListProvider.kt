package org.fenrirs.storage.statement

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.fenrirs.relay.models.FiltersX
import org.fenrirs.storage.service.PassListProvider


@Singleton
class PassListProvider @Inject constructor(
    private val sqlExec: StoredServiceImpl
) : PassListProvider {

    override suspend fun followedPubkeys(ownerPubkey: String): Set<String> =
        sqlExec.filterList(FiltersX {
            authors = setOf(ownerPubkey)
            kinds = setOf(3)
        })
            ?.firstOrNull()
            ?.tags
            ?.filter { it.isNotEmpty() && it[0] == "p" && it.size > 1 }
            ?.map { it[1] }
            ?.toSet()
            ?: emptySet()
}