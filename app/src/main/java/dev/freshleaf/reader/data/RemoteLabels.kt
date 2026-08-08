package dev.freshleaf.reader.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun normalizeFreshRssEndpoint(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    val url = trimmed.toHttpUrlOrNull()
        ?: throw FreshRssException("Enter a valid HTTPS FreshRSS URL")
    if (url.scheme != "https") throw FreshRssException("FreshRSS URL must use HTTPS")
    return if (url.encodedPath.endsWith("/api/greader.php")) trimmed else "$trimmed/api/greader.php"
}

internal fun freshRssClientLoginUrl(endpoint: String, path: String = "accounts/ClientLogin"): HttpUrl =
    "$endpoint/$path".toHttpUrl()

internal fun freshRssApiUrl(endpoint: String, path: String): HttpUrl =
    "$endpoint/reader/api/0/$path".toHttpUrl()

fun containsRemoteLabel(serializedIds: String, id: String): Boolean =
    serializedIds.split('\u001f').any { it == id }
