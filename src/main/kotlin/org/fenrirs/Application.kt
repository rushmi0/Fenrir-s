package org.fenrirs

import io.micronaut.runtime.Micronaut
import org.fenrirs.relay.model.ProfileSync
import java.io.InputStreamReader

fun main(args: Array<String>) {

    val classLoader = Thread.currentThread().contextClassLoader
    val bannerInputStream = classLoader.getResourceAsStream("banner.txt")

    bannerInputStream?.let {
        val bannerText = InputStreamReader(it).readText()
        println(bannerText)
    } ?: println("Banner not found.")

    val relay = Micronaut.build()
        .args(*args)
        .banner(false)
        .start()

    if (relay.isRunning) {
        relay.getBean(ProfileSync::class.java).sync()
    }

}
