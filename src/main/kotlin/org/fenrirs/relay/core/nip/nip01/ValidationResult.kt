package org.fenrirs.relay.core.nip.nip01

/**
 * ผลการตรวจสอบแบบเบา (low-allocation) ที่ใช้แทน Pair<Boolean, String> เดิม
 *
 * ภายในเก็บแค่ error message เป็น nullable String หนึ่งตัว: `null` หมายถึงผ่านการตรวจสอบ
 * เพราะเป็น @JvmInline value class ค่า [Valid] ที่คืนกลับตอนผ่านการตรวจสอบ (กรณีส่วนใหญ่)
 * จึงไม่ต้อง allocate object ใหม่ ต่างจาก Pair(true, "") เดิมที่ allocate ทุกครั้งและ box ค่า Boolean
 *
 * component1()/component2() ให้ syntax การ destructure แบบเดิม (status, warning) เพื่อไม่ต้องแก้โค้ดที่เรียกใช้
 */
@JvmInline
value class ValidationResult private constructor(private val error: String?) {

    val isValid: Boolean get() = error == null
    val reason: String get() = error ?: ""

    operator fun component1(): Boolean = isValid
    operator fun component2(): String = reason

    companion object {
        val Valid = ValidationResult(null)
        fun invalid(reason: String): ValidationResult = ValidationResult(reason)
    }
}