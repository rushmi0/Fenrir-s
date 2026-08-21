package org.fenrirs

import io.micronaut.runtime.Micronaut
import org.fenrirs.relay.core.RelayClock

fun main(args: Array<String>) {
	printBanner()
	RelayClock.startedAt // force init here, not on the first /admin/stats request
	Micronaut.build(*args)
		.banner(false)
		.start()
}

private fun printBanner() {
	object {}.javaClass.getResourceAsStream("/banner.txt")
		?.bufferedReader()
		?.use { println(it.readText()) }
}
