package org.fenrirs.relay.core.preload

import io.micronaut.scheduling.annotation.Scheduled

import jakarta.inject.Inject
import jakarta.inject.Singleton

import kotlinx.coroutines.runBlocking

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class PreloadScheduler @Inject constructor(
    private val snapshotBuilder: PreloadSnapshotBuilder,
    private val fileStore: PreloadFileStore
) {

    @Scheduled(fixedDelay = "3m", initialDelay = "3m")
    fun refresh() {
        runCatching { fileStore.write(runBlocking { snapshotBuilder.build() }) }
            .onFailure { LOG.error("[PRELOAD] Failed to refresh preload snapshot", it) }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(PreloadScheduler::class.java)
    }
}
