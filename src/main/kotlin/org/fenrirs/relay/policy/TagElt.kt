package org.fenrirs.relay.policy

import kotlinx.serialization.Serializable

@Serializable
sealed class TagElt {

    abstract val tag: String
    abstract val description: String
    abstract val nip: Set<String>

    companion object {

        fun parse(script: List<TagElt>): Set<String> = script.map { it.tag }.toSet()

        fun parse(script: TagElt): String = script.tag

        fun reverseParse(script: List<String>): Set<TagElt> = script.map { valueOf(it) }.toSet()

        fun valueOf(tag: String): TagElt {
            return when(tag) {
                "e" -> TAG_E
                "p" -> TAG_P
                "a" -> TAG_A
                "d" -> TAG_D
                "g" -> TAG_G
                "i" -> TAG_I
                "k" -> TAG_K
                "l" -> TAG_L
                "L" -> TAG_L_
                "m" -> TAG_M
                "q" -> TAG_Q
                "r" -> TAG_R
                "t" -> TAG_T
                "alt" -> ALT
                "amount" -> AMOUNT
                "bolt11" -> BOLT11
                "challenge" -> CHALLENGE
                "client" -> CLIENT
                "clone" -> CLONE
                "content-warning" -> CONTENT_WARNING
                "delegation" -> DELEGATION
                "description" -> DESCRIPTION
                "emoji" -> EMOJI
                "encrypted" -> ENCRYPTED
                "expiration" -> EXPIRATION
                "goal" -> GOAL
                "image" -> IMAGE
                "imeta" -> IMETA
                "lnurl" -> LNURL
                "location" -> LOCATION
                "name" -> NAME
                "nonce" -> NONCE
                "preimage" -> PREIMAGE
                "price" -> PRICE
                "proxy" -> PROXY
                "published_at" -> PUBLISHED_AT
                "relay" -> RELAY
                "relays" -> RELAYS
                "server" -> SERVER
                "subject" -> SUBJECT
                "summary" -> SUMMARY
                "thumb" -> THUMB
                "title" -> TITLE
                "web" -> WEB
                "zap" -> ZAP
                else -> throw IllegalArgumentException("Invalid Standardized Tag: $tag")
            }
        }

    }
}

data object TAG_E : TagElt() {
    override val tag: String get() = "e"
    override val description: String get() = "event id (hex)"
    override val nip: Set<String> get() = setOf("01", "10")
}

data object TAG_P : TagElt() {
    override val tag: String get() = "p"
    override val description: String get() = "pubkey (hex)"
    override val nip: Set<String> get() = setOf("01", "02")
}

data object TAG_A : TagElt() {
    override val tag: String get() = "a"
    override val description: String get() = "coordinates to an event"
    override val nip: Set<String> get() = setOf("01")
}

data object TAG_D : TagElt() {
    override val tag: String get() = "d"
    override val description: String get() = "identifier"
    override val nip: Set<String> get() = setOf("01")
}

data object TAG_G : TagElt() {
    override val tag: String get() = "g"
    override val description: String get() = "geohash"
    override val nip: Set<String> get() = setOf("52")
}

data object TAG_I : TagElt() {
    override val tag: String get() = "i"
    override val description: String get() = "identity"
    override val nip: Set<String> get() = setOf("39")
}

data object TAG_K : TagElt() {
    override val tag: String get() = "k"
    override val description: String get() = "kind number (string)"
    override val nip: Set<String> get() = setOf("18", "25", "72")
}

data object TAG_L : TagElt() {
    override val tag: String get() = "l"
    override val description: String get() = "label, label namespace"
    override val nip: Set<String> get() = setOf("32")
}

data object TAG_L_ : TagElt() {
    override val tag: String get() = "L"
    override val description: String get() = "label namespace"
    override val nip: Set<String> get() = setOf("32")
}

data object TAG_M : TagElt() {
    override val tag: String get() = "m"
    override val description: String get() = "MIME type"
    override val nip: Set<String> get() = setOf("94")
}

data object TAG_Q : TagElt() {
    override val tag: String get() = "q"
    override val description: String get() = "event id (hex)"
    override val nip: Set<String> get() = setOf("18")
}

data object TAG_R : TagElt() {
    override val tag: String get() = "r"
    override val description: String get() = "a reference (URL, etc)"
    override val nip: Set<String> get() = setOf("24", "65")
}

data object TAG_T : TagElt() {
    override val tag: String get() = "t"
    override val description: String get() = "hashtag"
    override val nip: Set<String> get() = setOf()
}

data object ALT : TagElt() {
    override val tag: String get() = "alt"
    override val description: String get() = "summary"
    override val nip: Set<String> get() = setOf("31")
}

data object AMOUNT : TagElt() {
    override val tag: String get() = "amount"
    override val description: String get() = "millisatoshis, stringified"
    override val nip: Set<String> get() = setOf("57")
}

data object BOLT11 : TagElt() {
    override val tag: String get() = "bolt11"
    override val description: String get() = "bolt11 invoice"
    override val nip: Set<String> get() = setOf("57")
}

data object CHALLENGE : TagElt() {
    override val tag: String get() = "challenge"
    override val description: String get() = "challenge string"
    override val nip: Set<String> get() = setOf("42")
}

data object CLIENT : TagElt() {
    override val tag: String get() = "client"
    override val description: String get() = "name, address"
    override val nip: Set<String> get() = setOf("89")
}

data object CLONE : TagElt() {
    override val tag: String get() = "clone"
    override val description: String get() = "git clone URL"
    override val nip: Set<String> get() = setOf("34")
}

data object CONTENT_WARNING : TagElt() {
    override val tag: String get() = "content-warning"
    override val description: String get() = "reason"
    override val nip: Set<String> get() = setOf("36")
}

data object DELEGATION : TagElt() {
    override val tag: String get() = "delegation"
    override val description: String get() = "pubkey, conditions, delegation token"
    override val nip: Set<String> get() = setOf("26")
}

data object DESCRIPTION : TagElt() {
    override val tag: String get() = "description"
    override val description: String get() = "description"
    override val nip: Set<String> get() = setOf("34", "57", "58")
}

data object EMOJI : TagElt() {
    override val tag: String get() = "emoji"
    override val description: String get() = "shortcode, image URL"
    override val nip: Set<String> get() = setOf("30")
}

data object ENCRYPTED : TagElt() {
    override val tag: String get() = "encrypted"
    override val description: String get() = "--"
    override val nip: Set<String> get() = setOf("90")
}

data object EXPIRATION : TagElt() {
    override val tag: String get() = "expiration"
    override val description: String get() = "unix timestamp (string)"
    override val nip: Set<String> get() = setOf("40")
}

data object GOAL : TagElt() {
    override val tag: String get() = "goal"
    override val description: String get() = "event id (hex)"
    override val nip: Set<String> get() = setOf("75")
}

data object IMAGE : TagElt() {
    override val tag: String get() = "image"
    override val description: String get() = "image URL"
    override val nip: Set<String> get() = setOf("23", "58")
}

data object IMETA : TagElt() {
    override val tag: String get() = "imeta"
    override val description: String get() = "inline metadata"
    override val nip: Set<String> get() = setOf("92")
}

data object LNURL : TagElt() {
    override val tag: String get() = "lnurl"
    override val description: String get() = "Lightning URL"
    override val nip: Set<String> get() = setOf("57")
}

data object LOCATION : TagElt() {
    override val tag: String get() = "location"
    override val description: String get() = "a string representing a location"
    override val nip: Set<String> get() = setOf("23")
}

data object NAME : TagElt() {
    override val tag: String get() = "name"
    override val description: String get() = "display name"
    override val nip: Set<String> get() = setOf("05", "23", "58")
}

data object NONCE : TagElt() {
    override val tag: String get() = "nonce"
    override val description: String get() = "a unique identifier for this proof-of-work"
    override val nip: Set<String> get() = setOf("13")
}

data object PREIMAGE : TagElt() {
    override val tag: String get() = "preimage"
    override val description: String get() = "sha256 preimage (hex)"
    override val nip: Set<String> get() = setOf("27")
}

data object PRICE : TagElt() {
    override val tag: String get() = "price"
    override val description: String get() = "amount in millisatoshis"
    override val nip: Set<String> get() = setOf("72")
}

data object PROXY : TagElt() {
    override val tag: String get() = "proxy"
    override val description: String get() = "node id"
    override val nip: Set<String> get() = setOf("24")
}

data object PUBLISHED_AT : TagElt() {
    override val tag: String get() = "published_at"
    override val description: String get() = "date, time"
    override val nip: Set<String> get() = setOf("85")
}

data object RELAY : TagElt() {
    override val tag: String get() = "relay"
    override val description: String get() = "relay URL"
    override val nip: Set<String> get() = setOf("33")
}

data object RELAYS : TagElt() {
    override val tag: String get() = "relays"
    override val description: String get() = "relay URLs"
    override val nip: Set<String> get() = setOf("65")
}

data object SERVER : TagElt() {
    override val tag: String get() = "server"
    override val description: String get() = "address, port, address type"
    override val nip: Set<String> get() = setOf("87")
}

data object SUBJECT : TagElt() {
    override val tag: String get() = "subject"
    override val description: String get() = "a subject"
    override val nip: Set<String> get() = setOf("23")
}

data object SUMMARY : TagElt() {
    override val tag: String get() = "summary"
    override val description: String get() = "a summary"
    override val nip: Set<String> get() = setOf("58")
}

data object THUMB : TagElt() {
    override val tag: String get() = "thumb"
    override val description: String get() = "thumbnail URL"
    override val nip: Set<String> get() = setOf("23")
}

data object TITLE : TagElt() {
    override val tag: String get() = "title"
    override val description: String get() = "title"
    override val nip: Set<String> get() = setOf("23")
}

data object WEB : TagElt() {
    override val tag: String get() = "web"
    override val description: String get() = "webpage URL"
    override val nip: Set<String> get() = setOf("89")
}

data object ZAP : TagElt() {
    override val tag: String get() = "zap"
    override val description: String get() = "Lightning Zaps"
    override val nip: Set<String> get() = setOf("57")
}
