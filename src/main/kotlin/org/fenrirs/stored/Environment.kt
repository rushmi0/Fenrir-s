package org.fenrirs.stored

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv

object Environment {

    private val dotenv: Dotenv = dotenv {
        directory = "." // root path
        filename = ".env"
    }

    val REDIS_URL: String by lazy { dotenv["REDIS_URL"] }

    val DATABASE_URL: String by lazy { dotenv["DATABASE_URL"] }
    val DATABASE_USERNAME: String by lazy { dotenv["DATABASE_USERNAME"] }
    val DATABASE_PASSWORD : String by lazy { dotenv["DATABASE_PASSWORD"] }

}

