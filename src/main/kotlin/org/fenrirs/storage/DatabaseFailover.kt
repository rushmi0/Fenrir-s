package org.fenrirs.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import kotlin.time.Duration.Companion.seconds

import org.fenrirs.relay.models.Event
import org.fenrirs.storage.table.EVENT

import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

import org.slf4j.LoggerFactory

/**
 * เช็คสถานะ primary DB ทุก 5 วินาที สลับไปใช้ secondary DB (H2) อัตโนมัติเมื่อ primary ล่ม
 * และ sync ข้อมูลที่เขียนไว้ใน secondary กลับเข้า primary อัตโนมัติเมื่อ primary ฟื้นตัว
 *
 * เริ่มทำงานจาก [DatabaseFactory.initialize] เฉพาะเมื่อ PRIMARY_DATABASE_ENABLED=true เท่านั้น
 */
object DatabaseFailover {

    private val LOG = LoggerFactory.getLogger(DatabaseFailover::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var onSecondary = false

    fun start() {
        checkNow()
        scope.launch {
            while (isActive) {
                delay(5.seconds)
                checkNow()
            }
        }
    }

    private fun checkNow() {
        val alive = pingPrimary()

        when {
            !alive && !onSecondary -> {
                onSecondary = true
                DatabaseFactory.activeDb = DatabaseFactory.secondaryDb
                LOG.warn("[DATABASE] Primary database unavailable")
                LOG.info("[FAILOVER] Switched to secondary database")
            }

            alive && onSecondary -> {
                onSecondary = false
                DatabaseFactory.activeDb = DatabaseFactory.primaryDb
                LOG.info("[DATABASE] Primary database reachable")
                LOG.info("[FAILOVER] Switched to primary database")
            }
        }

        if (alive) syncSecondaryToPrimary()
    }

    private fun pingPrimary(): Boolean = runCatching {
        transaction(DatabaseFactory.primaryDb) { exec("SELECT 1") }
    }.isSuccess

    private fun syncSecondaryToPrimary() {
        runCatching {
            val pending: List<Event> = transaction(DatabaseFactory.secondaryDb) {
                EVENT.selectAll().map { row ->
                    Event(
                        id = row[EVENT.EVENT_ID],
                        pubkey = row[EVENT.PUBKEY],
                        created_at = row[EVENT.CREATED_AT].toLong(),
                        kind = row[EVENT.KIND].toLong(),
                        tags = row[EVENT.TAGS],
                        content = row[EVENT.CONTENT],
                        sig = row[EVENT.SIG]
                    )
                }
            }
            if (pending.isEmpty()) return@runCatching

            LOG.info("[SYNC] Synchronizing {} events to primary database", pending.size)

            transaction(DatabaseFactory.primaryDb) {
                SchemaUtils.create(EVENT)
                EVENT.batchInsert(pending, ignore = true) { event ->
                    this[EVENT.EVENT_ID] = event.id!!
                    this[EVENT.PUBKEY] = event.pubkey!!
                    this[EVENT.CREATED_AT] = event.created_at!!.toInt()
                    this[EVENT.KIND] = event.kind!!.toInt()
                    this[EVENT.TAGS] = event.tags!!
                    this[EVENT.CONTENT] = event.content!!
                    this[EVENT.SIG] = event.sig!!
                }
            }

            val syncedIds = pending.map { it.id!! }
            transaction(DatabaseFactory.secondaryDb) {
                EVENT.deleteWhere { EVENT.EVENT_ID inList syncedIds }
            }

            LOG.info("[SYNC] Synchronization completed count={}", pending.size)
        }.onFailure { e -> LOG.error("[SYNC] Synchronization failed, will retry on next health check", e) }
    }
}
