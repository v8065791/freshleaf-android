package dev.freshleaf.reader.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

const val TAILSCALE_PACKAGE = "com.tailscale.ipn"

fun isTailscaleHostname(hostname: String): Boolean =
    hostname.trimEnd('.').lowercase().endsWith(".ts.net")

fun isTailscaleEndpoint(endpoint: String): Boolean =
    endpoint.trim().toHttpUrlOrNull()?.host?.let(::isTailscaleHostname) == true

class TailscaleConnectionException(cause: Throwable? = null) : FreshRssException(
    "Tailscale cannot reach this FreshRSS server. Check that Tailscale is connected and that FreshLeaf is not excluded by app-based split tunneling.",
    cause,
)

internal fun connectionFailure(hostname: String, cause: IOException): FreshRssException {
    if (isTailscaleHostname(hostname)) return TailscaleConnectionException(cause)
    val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.simpleName.orEmpty()
    return FreshRssException("Unable to reach FreshRSS: $detail", cause)
}
