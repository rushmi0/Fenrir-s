package org.fenrirs.relay.core

/** Process start time, for the "uptime" stat on the admin Dashboard. A Kotlin `object` only runs
 * its initializer on first access, so `main()` touches [startedAt] before starting
 * Micronaut - otherwise this would read as "time of the first stats request", not server start. */
object RelayClock {
    val startedAt: Long = System.currentTimeMillis() / 1000
}
