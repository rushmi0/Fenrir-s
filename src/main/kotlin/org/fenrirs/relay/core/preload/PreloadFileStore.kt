package org.fenrirs.relay.core.preload

import io.micronaut.serde.ObjectMapper

import jakarta.inject.Inject
import jakarta.inject.Singleton

import org.fenrirs.storage.StoragePaths

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicReference

@Singleton
class PreloadFileStore @Inject constructor(private val objectMapper: ObjectMapper) {

    private val file = StoragePaths.resolve(FILE_NAME)
    private val cachedSnapshot = AtomicReference<PreloadSnapshot?>(null)

    fun write(snapshot: PreloadSnapshot) {
        val json = objectMapper.writeValueAsString(snapshot)
        val tempFile = StoragePaths.resolve("$FILE_NAME.tmp")
        tempFile.writeText(json)
        Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        cachedSnapshot.set(snapshot)
    }

    fun readCurrent(): PreloadSnapshot? = cachedSnapshot.get() ?: readFromDisk()

    private fun readFromDisk(): PreloadSnapshot? {
        if (!file.exists()) return null
        return runCatching { objectMapper.readValue(file.readText(), PreloadSnapshot::class.java) }.getOrNull()
    }

    companion object {
        private const val FILE_NAME = "preload.json"
    }
}
