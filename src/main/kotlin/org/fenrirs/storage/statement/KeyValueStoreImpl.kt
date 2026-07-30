package org.fenrirs.storage.statement

import jakarta.inject.Singleton

import org.fenrirs.storage.DatabaseFactory.configTask
import org.fenrirs.storage.service.KeyValueStore
import org.fenrirs.storage.table.KV_STORE

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert

@Singleton
object KeyValueStoreImpl : KeyValueStore {

    override fun get(key: String): String? = configTask {
        KV_STORE.selectAll().where { KV_STORE.KEY eq key }.firstOrNull()?.get(KV_STORE.VALUE)
    }

    override fun set(key: String, value: String) {
        configTask {
            KV_STORE.upsert {
                it[KEY] = key
                it[VALUE] = value
            }
        }
    }

    override fun sync(defaults: Map<String, String>) = configTask {
        defaults.forEach { (key, value) ->
            KV_STORE.upsert {
                it[KEY] = key
                it[VALUE] = value
            }
        }
    }
}