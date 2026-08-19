package org.fenrirs.storage.service

data class Operator(val pubkey: String, val role: String, val createdAt: Long)

interface OperatorStore {

    /** true เมื่อยังไม่มี operator ใด ๆ ในระบบเลย - ใช้บ่งบอกสถานะ INITIAL_SETUP */
    fun isEmpty(): Boolean

    fun find(pubkey: String): Operator?

    fun all(): List<Operator>

    fun add(pubkey: String, role: String)

    fun remove(pubkey: String)
}
