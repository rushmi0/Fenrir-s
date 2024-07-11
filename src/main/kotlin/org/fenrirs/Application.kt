package org.fenrirs

import io.micronaut.runtime.Micronaut.run
import org.fenrirs.relay.service.ProfileSync

fun main(args: Array<String>) {

    val relay = run(*args)

    if (relay.isRunning) {
        relay.getBean(ProfileSync::class.java).sync()
    }

}
