package dev.freshleaf.reader.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

open class FreshRssException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class FreshRssSnapshot(
    val feeds: List<FeedEntity>,
    val categories: List<CategoryEntity>,
    val tags: List<TagEntity>,
    val articles: List<ArticleEntity>,
)

class FreshRssApi(
    private val clientFactory: EndpointHttpClientFactory = FixedEndpointHttpClientFactory(OkHttpClient()),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    constructor(context: Context) : this(clientFactory = AndroidEndpointHttpClientFactory(context))
    constructor(http: OkHttpClient, json: Json = Json { ignoreUnknownKeys = true }) : this(FixedEndpointHttpClientFactory(http), json)

    private var endpoint: String = ""
    private var http: OkHttpClient = OkHttpClient()
    private var username: String = ""
    private var auth: String? = null
    private var token: String? = null

    suspend fun login(endpoint: String, username: String, password: String) {
        this.endpoint = normalizeEndpoint(endpoint)
        http = clientFactory.clientFor(this.endpoint.toHttpUrl())
        this.username = username
        val response = postRaw("accounts/ClientLogin", mapOf("Email" to username, "Passwd" to password))
        val authLine = response.lineSequence().firstOrNull { it.startsWith("Auth=") }
            ?.substringAfter("Auth=")?.trim()
            ?: throw FreshRssException("FreshRSS did not return an Auth token")
        auth = authLine
        token = null
    }

    suspend fun sync(): FreshRssSnapshot {
        requireLoggedIn()
        val tagsJson = getJson("tag/list")
        val subscriptionsJson = getJson("subscription/list")
        val unreadJson = getJson("unread-count")
        val itemsJson = getJson("stream/contents/reading-list", mapOf("n" to "200", "output" to "json"))

        val unreadCounts = unreadJson["unreadcounts"]?.jsonArray.orEmpty().associate {
            it.jsonObject.string("id") to it.jsonObject.int("count")
        }
        val categoryIds = subscriptionsJson["subscriptions"]?.jsonArray.orEmpty()
            .flatMap { it.jsonObject["categories"]?.jsonArray.orEmpty().map { c -> c.jsonObject.string("id") } }
            .toSet()
        val categories = tagsJson["tags"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.toCategoryOrNull() }
        val tags = tagsJson["tags"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject.toTagOrNull(categoryIds) }
        val feeds = subscriptionsJson["subscriptions"]?.jsonArray.orEmpty().mapNotNull { value ->
            val obj = value.jsonObject
            val id = obj.stringOrNull("id") ?: return@mapNotNull null
            val categoriesForFeed = obj["categories"]?.jsonArray.orEmpty()
                .mapNotNull { it.jsonObject.stringOrNull("id") }
                .filter { it in categoryIds }
            FeedEntity(
                id = id,
                title = obj.string("title", id.substringAfterLast('/')),
                url = obj.string("htmlUrl", id.removePrefix("feed/")),
                siteUrl = obj.string("htmlUrl", id.removePrefix("feed/")),
                categoryIds = categoriesForFeed.joinToString("\u001f"),
                unreadCount = unreadCounts[id] ?: 0,
            )
        }
        val feedIds = feeds.map { it.id }.toSet()
        val articles = itemsJson["items"]?.jsonArray.orEmpty().mapNotNull { value ->
            value.jsonObject.toArticleOrNull(feedIds)
        }
        return FreshRssSnapshot(feeds, categories, tags, articles)
    }

    suspend fun markRead(articleId: String, read: Boolean) {
        editTag(articleId, add = if (read) "user/-/state/com.google/read" else null, remove = if (!read) "user/-/state/com.google/read" else null)
    }

    suspend fun markStarred(articleId: String, starred: Boolean) {
        editTag(articleId, add = if (starred) "user/-/state/com.google/starred" else null, remove = if (!starred) "user/-/state/com.google/starred" else null)
    }

    suspend fun subscribe(feedUrl: String, title: String, categoryId: String? = null) {
        val fields = mutableMapOf("s" to "feed/$feedUrl", "ac" to "subscribe", "t" to title)
        if (categoryId != null) fields["a"] = categoryId
        post("subscription/edit", fields)
    }

    suspend fun unsubscribe(feedId: String) {
        post("subscription/edit", mapOf("s" to feedId, "ac" to "unsubscribe"))
    }

    suspend fun createTag(label: String): String {
        val id = "user/$username/label/${label.trim()}"
        post("tag/edit", mapOf("s" to id, "pub" to "false"))
        return id
    }

    suspend fun deleteTag(tagId: String) {
        post("disable-tag", mapOf("s" to tagId, "ac" to "disable-tags"))
    }

    private suspend fun editTag(articleId: String, add: String?, remove: String?) {
        val fields = mutableMapOf("i" to articleId, "ac" to "edit")
        if (add != null) fields["a"] = add
        if (remove != null) fields["r"] = remove
        post("edit-tag", fields)
    }

    private suspend fun getJson(path: String, query: Map<String, String> = emptyMap()): JsonObject {
        val builder = url(path).newBuilder()
        query.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder().url(builder.build()).authorized().get().build()
        return execute(request).let { body ->
            runCatching { json.parseToJsonElement(body).jsonObject }
                .getOrElse { throw FreshRssException("Invalid FreshRSS JSON from $path", it) }
        }
    }

    private suspend fun post(path: String, fields: Map<String, String>) {
        if (token.isNullOrBlank()) refreshToken()
        val requestFields = fields + ("T" to requireNotNull(token))
        val form = FormBody.Builder().apply { requestFields.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url(url(path)).authorized().post(form).build()
        execute(request)
    }

    private suspend fun postRaw(path: String, fields: Map<String, String>): String {
        val form = FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        return execute(Request.Builder().url(endpointUrl(path)).post(form).build())
    }

    private suspend fun execute(request: Request): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw FreshRssException("FreshRSS HTTP ${response.code}: ${body.take(200)}")
                body
            }
        } catch (e: FreshRssException) {
            throw e
        } catch (e: IOException) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName.orEmpty()
            throw FreshRssException("Unable to reach FreshRSS: $detail", e)
        }
    }

    private fun Request.Builder.authorized(): Request.Builder = apply {
        auth?.let { header("Authorization", "GoogleLogin auth=$it") }
    }

    private fun endpointUrl(path: String) = freshRssClientLoginUrl(endpoint, path)

    private fun url(path: String) = freshRssApiUrl(endpoint, path)

    private fun requireLoggedIn() {
        if (endpoint.isBlank() || auth.isNullOrBlank()) throw FreshRssException("FreshRSS account is not configured")
    }

    private fun normalizeEndpoint(value: String) = normalizeFreshRssEndpoint(value)

    private suspend fun refreshToken() {
        token = execute(Request.Builder().url(url("token")).authorized().get().build()).trim()
    }

    private fun Map<String, JsonElement>.string(key: String, fallback: String = "") = stringOrNull(key) ?: fallback
    private fun Map<String, JsonElement>.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun Map<String, JsonElement>.int(key: String): Int = stringOrNull(key)?.toIntOrNull() ?: 0

    private fun JsonObject.toCategoryOrNull(): CategoryEntity? {
        val id = stringOrNull("id") ?: return null
        val label = stringOrNull("label") ?: return null
        return if (id.contains("/label/")) CategoryEntity(id, label) else null
    }

    private fun JsonObject.toTagOrNull(categoryIds: Set<String>): TagEntity? {
        val id = stringOrNull("id") ?: return null
        val label = stringOrNull("label") ?: return null
        return if (id.contains("/label/") && id !in categoryIds) TagEntity(id, label) else null
    }

    private fun JsonObject.toArticleOrNull(feedIds: Set<String>): ArticleEntity? {
        val id = stringOrNull("id") ?: return null
        val origin = this["origin"]?.jsonObject
        val feedId = origin?.stringOrNull("streamId")?.takeIf { it in feedIds }
            ?: stringOrNull("crawlTimeMsec")?.let { "feed/unknown" }
            ?: return null
        val author = this["author"]?.jsonArray?.firstOrNull()?.jsonObject?.string("name") ?: ""
        val summary = this["summary"]?.jsonObject?.stringOrNull("content")
            ?: this["content"]?.jsonArray?.firstOrNull()?.jsonObject?.stringOrNull("content")
            ?: ""
        val url = this["alternate"]?.jsonArray?.firstOrNull()?.jsonObject?.string("href")
            ?: origin?.string("htmlUrl") ?: ""
        val timestamp = stringOrNull("crawlTimeMsec")?.toLongOrNull()
            ?: stringOrNull("timestampUsec")?.toLongOrNull()?.div(1000)
            ?: 0L
        val labels = this["categories"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }
        return ArticleEntity(
            id = id,
            feedId = feedId,
            title = string("title", "Untitled"),
            author = author,
            html = summary,
            url = url,
            publishedAt = timestamp,
            isRead = labels.any { it.endsWith("/state/com.google/read") },
            isStarred = labels.any { it.endsWith("/state/com.google/starred") },
            tagIds = labels.filter { it.contains("/label/") }.joinToString("\u001f"),
        )
    }
}
