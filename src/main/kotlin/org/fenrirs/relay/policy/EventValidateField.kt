package org.fenrirs.relay.policy

import java.util.*

enum class EventValidateField(
    override val fieldName: String,
    override val fieldType: Class<*>,
    override val fieldCollectionType: Class<*>? = null
) : NostrField {
    ID("id", String::class.java),
    PUBKEY("pubkey", String::class.java),
    CREATE_AT("created_at", Long::class.java),
    CONTENT("content", String::class.java),
    KIND("kind", Long::class.java),
    TAGS("tags", ArrayList::class.java),
    SIGNATURE("sig", String::class.java)
}