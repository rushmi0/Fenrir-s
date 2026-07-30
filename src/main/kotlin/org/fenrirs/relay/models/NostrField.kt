package org.fenrirs.relay.models

interface NostrFieldTypeProvider {
    val fieldType: Class<*>
}

interface NostrField : NostrFieldTypeProvider {
    val fieldName: String
    val fieldCollectionType: Class<*>? get() = null
}
