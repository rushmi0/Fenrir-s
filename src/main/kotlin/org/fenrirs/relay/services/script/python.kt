package org.fenrirs.relay.services.script

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray


class ComputedArray : ProxyArray {
    override fun get(index: Long): Long = index * 2;

    override fun set(index: Long, value: Value?) {
        throw UnsupportedOperationException()
    }

    override fun getSize(): Long = 1_000_000_001L;
}

fun main() {
    Context.newBuilder()
        .allowAllAccess(true)
        .build().use { ctx ->
            val arr = ComputedArray()
            ctx.polyglotBindings.putMember("arr", arr)
            val result = ctx.eval(
                "python",
                """
                    import polyglot
                    arr = polyglot.import_value('arr')
                    arr[1] + arr[1000000000]
                """.trimIndent()
            )
                .asLong()
            assert(result == 2000000002L)
            println(result)
        }
}
