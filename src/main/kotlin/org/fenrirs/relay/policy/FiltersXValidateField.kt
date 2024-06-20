package org.fenrirs.relay.policy

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
    SEARCH("search", String::class.java),
    TAG_E("#e", ArrayList::class.java),
    TAG_P("#p", ArrayList::class.java),
    TAG_A("#a", ArrayList::class.java),
    TAG_D("#d", ArrayList::class.java),
    TAG_G("#g", ArrayList::class.java),
    TAG_I("#i", ArrayList::class.java),
    TAG_K("#k", ArrayList::class.java),
    TAG_L("#l", ArrayList::class.java),
    TAG_L_("#L", ArrayList::class.java),
    TAG_M("#m", ArrayList::class.java),
    TAG_Q("#q", ArrayList::class.java),
    TAG_R("#r", ArrayList::class.java),
    TAG_T("#t", ArrayList::class.java)
}