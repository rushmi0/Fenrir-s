package org.fenrirs.relay.models

import java.util.ArrayList


enum class FiltersXValidateField(
    override val fieldName: String,
    override val fieldType: Class<*>,
    override val fieldCollectionType: Class<*>? = null
) : NostrField {
    IDS("ids", ArrayList::class.java),
    AUTHORS("authors", ArrayList::class.java),
    KINDS("kinds", ArrayList::class.java),
    SINCE("since", Long::class.java),
    UNTIL("until", Long::class.java),
    LIMIT("limit", Long::class.java),
    SEARCH("search", String::class.java)
}