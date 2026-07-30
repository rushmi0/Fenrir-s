package org.fenrirs.relay.core.nip.nip11

import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.web.router.exceptions.DuplicateRouteException

import jakarta.inject.Inject

/**
 * Gateway ("/") ถูกประกาศเป็น @ServerWebSocket ทำให้เฟรมเวิร์กจดทะเบียนเส้นทาง GET "/" ภายใน
 * สำหรับขั้นตอน handshake โดยอัตโนมัติเสมอ การประกาศ @Controller("/") @Get แยกต่างหากจึงชนกับ
 * เส้นทางนั้นทุกครั้งและทำให้เกิด DuplicateRouteException จึงต้องดักจับ exception นี้แทน
 * เพื่อตอบกลับ NIP-11 หรือหน้าเว็บแอปตาม Accept header ของคำขอ HTTP ปกติที่ไม่ใช่ WebSocket upgrade
 */
@Controller
class RelayInfoController @Inject constructor(private val nip11: RelayInformation) {

    @Error(exception = DuplicateRouteException::class, global = true)
    fun index(request: HttpRequest<*>): HttpResponse<String> {
        val accept = request.headers.get(HttpHeaders.ACCEPT)
        val contentType = if (accept == "application/nostr+json") MediaType.APPLICATION_JSON else MediaType.TEXT_HTML
        return HttpResponse.ok(nip11.loadRelayInfo(contentType))
            .contentType(contentType)
            .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
    }
}