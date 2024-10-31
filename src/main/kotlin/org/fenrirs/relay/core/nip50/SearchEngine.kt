package org.fenrirs.relay.core.nip50

import jakarta.inject.Singleton
import org.jetbrains.exposed.sql.*


@Singleton
class SearchEngine {


    /**
     * คลาสที่ใช้ในการสร้างฟังก์ชัน `to_tsvector` ของ PostgreSQL
     * - ฟังก์ชันนี้ใช้เพื่อแปลงข้อความเป็น `tsvector` สำหรับการค้นหาข้อความ
     * - `config` คือค่าการตั้งค่า
     * - `text` คือข้อความที่ต้องการแปลง
     */
    class toTsvector(
        config: Expression<*>,
        text: ExpressionWithColumnType<String>,
    ) : CustomFunction<String>(
        functionName = "to_tsvector",
        columnType = TextColumnType(),
        expr = arrayOf(config, text)
    )


    /**
     * คลาสที่ใช้ในการสร้างฟังก์ชัน `plainto_tsquery` ของ PostgreSQL
     * - ฟังก์ชันนี้ใช้เพื่อแปลงข้อความเป็น `tsquery` สำหรับการค้นหาข้อความ
     * - `config` คือค่าการตั้งค่า
     * - `text` คือข้อความที่ต้องการแปลง
     */
    class plainToTsquery(
        config: Expression<*>,
        text: ExpressionWithColumnType<String>,
    ) : CustomFunction<String>(
        functionName = "plainto_tsquery",
        columnType = TextColumnType(),
        expr = arrayOf(config, text)
    )


    /**
     * คลาสที่ใช้สำหรับการเปรียบเทียบ `tsvector` กับ `tsquery` โดยใช้ตัวดำเนินการ `@@`
     * - ตัวดำเนินการนี้ใช้ในการค้นหาข้อความใน PostgreSQL
     */
    class MatchOp(expr1: Expression<*>, expr2: Expression<*>) : ComparisonOp(expr1, expr2, "@@")

    /**
     * ฟังก์ชันสำหรับสร้างการเปรียบเทียบ `tsvector` กับ `tsquery`
     * - ใช้ตัวดำเนินการ `@@` สำหรับการค้นหาข้อความ
     */
    infix fun Expression<*>.match(t: Expression<*>): Op<Boolean> = MatchOp(this, t)


    /**
     * ฟังก์ชันสำหรับสร้างการค้นหาข้อความในฐานข้อมูล
     * - ฟังก์ชันนี้ใช้ `to_tsvector` เพื่อแปลงคอลัมน์ข้อความเป็น `tsvector`
     * - และใช้ `plainto_tsquery` เพื่อแปลงข้อความค้นหาเป็น `tsquery`
     * - ผลลัพธ์คือการเปรียบเทียบระหว่าง `tsvector` กับ `tsquery` โดยใช้ตัวดำเนินการ `@@`
     *
     * @param column คอลัมน์ที่เก็บข้อความสำหรับการค้นหา
     * @param search ข้อความที่ต้องการค้นหาในคอลัมน์
     * @return Op<Boolean> ผลลัพธ์การเปรียบเทียบระหว่าง `tsvector` และ `tsquery`
     */
    fun tsQuery(column: Column<String>, search: String): Op<Boolean> {
        val simple: LiteralOp<String> = stringLiteral("simple")
        return toTsvector(simple, column) match plainToTsquery(
            simple,
            stringLiteral(search)
        )
    }


}