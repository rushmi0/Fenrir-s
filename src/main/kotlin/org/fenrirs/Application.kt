package org.fenrirs

import io.micronaut.runtime.Micronaut
import java.io.InputStreamReader

import org.graalvm.polyglot.Context;

fun main(args: Array<String>) {

    val classLoader = Thread.currentThread().contextClassLoader
    val bannerInputStream = classLoader.getResourceAsStream("banner.txt")

    Context.create().use { context ->
        context.eval("js", "console.log('Hello from GraalJS!')")
    }

    bannerInputStream?.let {
        val bannerText = InputStreamReader(it).readText()
        println(bannerText)
    } ?: println("Banner not found.")

    Micronaut.build()
        .args(*args)
        .banner(false)
        .start();
}
