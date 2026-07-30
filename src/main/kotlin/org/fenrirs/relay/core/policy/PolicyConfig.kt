package org.fenrirs.relay.core.policy


interface PolicyConfig {

    /** ต้องยืนยันตัวตนตาม NIP-42 ก่อนถึงจะใช้คำสั่งที่ถูกป้องกันได้หรือไม่ (ดู AuthenticationRule) */
    val AUTH_ENABLED: Boolean

    /** pubkey (normalize เป็น hex แล้ว) ที่ข้ามการบังคับ AUTH_ENABLED ได้เสมอ (ดู WhitelistRule) */
    val AUTH_WHITELIST_PUBKEYS: Set<String>

    /** pubkey (hex) ของเจ้าของ relay - เผยแพร่ event ได้เสมอไม่ว่านโยบายอื่นจะเข้มงวดแค่ไหน (ดู RelayOwnerRule) */
    val RELAY_OWNER: String

    /** เปิดกว้างให้ทุกคนเผยแพร่ event ได้โดยไม่ต้องทำ Proof of Work (เมื่อ FOLLOWS_PASS=false) (ดู PassListRule) */
    val ALL_PASS: Boolean

    /** เผยแพร่ event ได้โดยไม่ต้องทำ Proof of Work เฉพาะ pubkey ที่เจ้าของ relay ติดตามอยู่ (ดู PassListRule) */
    val FOLLOWS_PASS: Boolean

    /** บังคับให้ event ที่ไม่ผ่านทางลัดอื่นต้องแนบ Proof of Work ที่ผ่านเกณฑ์ (ดู ProofOfWorkRule) */
    val PROOF_OF_WORK_ENABLED: Boolean
}