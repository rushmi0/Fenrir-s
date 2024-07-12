package org.fenrirs

import io.micronaut.runtime.Micronaut
import org.fenrirs.relay.service.ProfileSync

fun main(args: Array<String>) {

    println("""
    ___________                 .__                         
    \_   _____/___   ___________|__|______            ______
     |    __)/ __ \ /    \_  __ \  \_  __ \  ______  /  ___/
     |     \\  ___/|   |  \  | \/  ||  | \/ /_____/  \___ \ 
     \___  / \___  >___|  /__|  |__||__|            /____  >
         \/      \/     \/                               \/ 
         version 0.1.0 by rushmi0

    """.trimIndent())
    val relay = Micronaut.build()
        .args(*args)
        .banner(false)
        .start()

    if (relay.isRunning) {
        relay.getBean(ProfileSync::class.java).sync()
    }

}
