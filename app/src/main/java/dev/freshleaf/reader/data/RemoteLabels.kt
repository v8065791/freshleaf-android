package dev.freshleaf.reader.data

fun normalizeFreshRssEndpoint(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    return if (trimmed.endsWith("/api/greader.php")) trimmed else "$trimmed/api/greader.php"
}

fun containsRemoteLabel(serializedIds: String, id: String): Boolean =
    serializedIds.split('\u001f').any { it == id }

