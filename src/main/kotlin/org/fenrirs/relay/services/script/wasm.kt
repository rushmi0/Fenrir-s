package org.fenrirs.relay.services.script

import kotlinx.coroutines.Dispatchers.Main
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source

fun main() {
    Context.create().use { context ->
        val wasmFile = Main::class.java.getResource("/wasm/add-two.wasm")
        val mainModule = context.eval(Source.newBuilder("wasm", wasmFile).build())
        val mainInstance = mainModule.newInstance()
        val addTwo = mainInstance.getMember("exports").getMember("addTwo")
        println("addTwo(40, 2) = " + addTwo.execute(40, 2))
    }
}