package org.fenrirs.relay.web.admin

import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.hateoas.JsonError
import io.micronaut.serde.annotation.Serdeable

import org.fenrirs.relay.web.AdminAuthFilter

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpHeaders
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Serdeable
data class LinkPreviewDto(
    val url: String,
    val title: String?,
    val description: String?,
    val image: String?
)

private data class FetchResult(val statusCode: Int, val headers: HttpHeaders, val body: String)

private data class ParsedMeta(val title: String?, val description: String?, val image: String?)

/**
 * Server-side fetch-and-unfurl for URLs found in note content (see the Feed's NoteContent
 * renderer) - exists because browsers can't read cross-origin page HTML themselves (CORS), so a
 * link preview card needs *something* same-origin to ask instead.
 *
 * [isSafeHost] deliberately restricts this to public http(s) hosts so this relay can't be turned
 * into an SSRF proxy into its own internal network (localhost, RFC1918/CGN/ULA ranges, etc.) -
 * every hop of a redirect chain is re-validated the same way, not just the original URL. This
 * covers the common ranges, not every DNS-rebinding/IPv4-mapped-IPv6 edge case; it's scoped for an
 * authenticated admin previewing their own feed, not a public-facing input surface.
 */
@Controller("/inter/api/v1/admin/preview")
@Produces(MediaType.APPLICATION_JSON)
class LinkPreviewController {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(FETCH_TIMEOUT_SECS))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()

    @Get
    fun preview(
        @RequestAttribute(AdminAuthFilter.ROLE_ATTRIBUTE) role: String,
        @QueryValue url: String
    ): HttpResponse<*> {
        if (!RoleGuard.canAccessConsole(role)) return RoleGuard.forbidden("general users cannot access the Admin Console")
        var current = runCatching { URI(url) }.getOrNull() ?: return badRequest("invalid url")

        var body: String? = null
        var attempts = 0
        while (attempts <= MAX_REDIRECTS) {
            attempts++
            if (!isSafeHost(current)) return badRequest("that url isn't reachable")

            val result = runCatching { fetch(current) }.getOrNull()
                ?: return badGateway("failed to reach that url")

            when (result.statusCode) {
                in 300..399 -> {
                    val location = result.headers.firstValue("location").orElse(null)
                        ?: return badGateway("redirect with no location")
                    current = runCatching { current.resolve(location) }.getOrNull()
                        ?: return badRequest("invalid redirect target")
                }
                in 200..299 -> body = result.body
                else -> return badGateway("that url returned HTTP ${result.statusCode}")
            }
            if (body != null) break
        }

        val html = body ?: return badGateway("too many redirects")
        val meta = parseMeta(html, current)
        return HttpResponse.ok(
            LinkPreviewDto(url = current.toString(), title = meta.title, description = meta.description, image = meta.image)
        )
    }

    private fun fetch(uri: URI): FetchResult {
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(FETCH_TIMEOUT_SECS))
            .header("User-Agent", "Fenrir-relay-link-preview/1.0")
            .GET()
            .build()
        val response = client.send(request, BodyHandlers.ofInputStream())
        val body = response.body().use { stream ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var total = 0
            while (total < MAX_BODY_BYTES) {
                val read = stream.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
                total += read
            }
            out.toString(Charsets.UTF_8)
        }
        return FetchResult(response.statusCode(), response.headers(), body)
    }

    private fun badRequest(message: String): HttpResponse<*> =
        HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST).body(JsonError(message))

    private fun badGateway(message: String): HttpResponse<*> =
        HttpResponse.status<JsonError>(HttpStatus.BAD_GATEWAY).body(JsonError(message))

    companion object {
        private const val MAX_REDIRECTS = 3
        private const val MAX_BODY_BYTES = 2_000_000
        private const val FETCH_TIMEOUT_SECS = 5L

        private fun isSafeHost(uri: URI): Boolean {
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            val host = uri.host ?: return false
            if (host.equals("localhost", ignoreCase = true)) return false

            val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull()
            if (addresses.isNullOrEmpty()) return false
            return addresses.all(::isPublicAddress)
        }

        private fun isPublicAddress(addr: InetAddress): Boolean {
            if (addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
                addr.isAnyLocalAddress || addr.isMulticastAddress
            ) return false

            val bytes = addr.address
            return when (bytes.size) {
                4 -> !isCarrierGradeNat(bytes) // 100.64.0.0/10
                16 -> !isUniqueLocalIPv6(bytes) // fc00::/7
                else -> true
            }
        }

        /** 100.64.0.0/10 - shared address space for carrier-grade NAT, not covered by [InetAddress.isSiteLocalAddress]. */
        private fun isCarrierGradeNat(b: ByteArray): Boolean =
            b[0] == 100.toByte() && (b[1].toInt() and 0xC0) == 0x40

        /** fc00::/7 - IPv6 unique local addresses, the IPv6 analogue of RFC1918. */
        private fun isUniqueLocalIPv6(b: ByteArray): Boolean =
            (b[0].toInt() and 0xFE) == 0xFC

        private val metaTagRegex = Regex("<meta\\s+[^>]*>", RegexOption.IGNORE_CASE)
        private val titleTagRegex = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        // attr() runs per attribute name (property/name/content) for every <meta> tag on the
        // fetched page - cache the compiled pattern per name instead of recompiling it on each call.
        private val attrRegexCache = ConcurrentHashMap<String, Regex>()

        private fun attr(tag: String, name: String): String? =
            attrRegexCache.getOrPut(name) {
                Regex("\\b$name\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            }.find(tag)?.groupValues?.get(1)

        private fun unescapeHtml(s: String): String = s
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()

        private fun parseMeta(html: String, base: URI): ParsedMeta {
            var ogTitle: String? = null
            var ogDescription: String? = null
            var ogImage: String? = null
            var plainDescription: String? = null

            for (match in metaTagRegex.findAll(html)) {
                val tag = match.value
                val property = (attr(tag, "property") ?: attr(tag, "name"))?.lowercase() ?: continue
                val content = attr(tag, "content") ?: continue
                when (property) {
                    "og:title", "twitter:title" -> if (ogTitle == null) ogTitle = content
                    "og:description", "twitter:description" -> if (ogDescription == null) ogDescription = content
                    "og:image", "og:image:url", "twitter:image" -> if (ogImage == null) ogImage = content
                    "description" -> if (plainDescription == null) plainDescription = content
                }
            }

            val title = (ogTitle ?: titleTagRegex.find(html)?.groupValues?.get(1))?.let(::unescapeHtml)?.take(200)
            val description = (ogDescription ?: plainDescription)?.let(::unescapeHtml)?.take(300)
            val image = ogImage?.let(::unescapeHtml)?.let { raw -> runCatching { base.resolve(raw).toString() }.getOrNull() }

            return ParsedMeta(title = title?.ifBlank { null }, description = description?.ifBlank { null }, image = image)
        }
    }
}
