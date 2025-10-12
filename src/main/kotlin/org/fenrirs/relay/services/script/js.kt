package org.fenrirs.relay.services.script

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.HostAccess

fun main() {

    Context.create().use { context ->
        context.eval("js", "console.log('Hello from GraalJS!')")
    }
    val script = """
        let _a = 3; 
        let _b = 2; 
        console.log(`sum: ${'$'}{_a+_b}`);
    """.trimIndent()

    val ctx = Context.newBuilder("js")
        .allowHostAccess(HostAccess.ALL)
        .allowHostClassLookup { true }
        .build();
    ctx.eval("js", script);

}