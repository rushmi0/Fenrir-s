package org.fenrirs.storage.service

interface KeyValueStore {

    fun get(key: String): String?

    fun set(key: String, value: String)

    fun sync(defaults: Map<String, String>)
}