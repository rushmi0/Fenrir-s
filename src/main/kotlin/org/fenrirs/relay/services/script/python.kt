package org.fenrirs.relay.services.script

import org.graalvm.polyglot.Context

fun main() {
    Context.create().use { context ->
        context.eval("python", "print('Hello from GraalPy!')")
    }
}