package org.fenrirs

import io.micronaut.runtime.Micronaut

fun main(args: Array<String>) {
	printBanner()
	Micronaut.build(*args)
		.banner(false)
		.start()
}

private fun printBanner() {
	object {}.javaClass.getResourceAsStream("/banner.txt")
		?.bufferedReader()
		?.use { println(it.readText()) }
}
