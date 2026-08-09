package dev.freshleaf.reader.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
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

internal data class SubscriptionCategoryChanges(val add: Set<String>, val remove: Set<String>)

internal fun subscriptionCategoryChanges(currentIds: Set<String>, selectedIds: Set<String>) =
    SubscriptionCategoryChanges(add = selectedIds - currentIds, remove = currentIds - selectedIds)

class FreshRssApi(
    private val http: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private var endpoint: String = ""
    private var username: String = ""
    private var auth: String? = null
    private var token: String? = null

    suspend fun login(endpoint: String, username: String, password: String) {
        this.endpoint = normalizeEndpoint(endpoint)
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
        val jsonOutput = mapOf("output" to "json")
        val tagsJson = getJson("tag/list", jsonOutput)
        val subscriptionsJson = getJson("subscription/list", jsonOutput)
        val unreadJson = getJson("unread-count", jsonOutput)
        val itemsJson = getJson(
            "stream/contents/user/-/state/com.google/reading-list",
            jsonOutput + ("n" to "200"),
        )

        val unreadCounts = unreadJson["unreadcounts"].asArray().mapNotNull { it as? JsonObject }.associate {
            it.string("id") to it.int("count")
        }
        val subscriptions = subscriptionsJson["subscriptions"].asArray().mapNotNull { it as? JsonObject }
        val tagObjects = tagsJson["tags"].asArray().mapNotNull { it as? JsonObject }
        val categoryIds = subscriptions
            .flatMap { it["categories"].asArray().mapNotNull { c -> (c as? JsonObject)?.stringOrNull("id") } }
            .toSet()
        val categories = tagObjects.mapNotNull { it.toCategoryOrNull() }
        val tags = tagObjects.mapNotNull { it.toTagOrNull(categoryIds) }
        val feeds = subscriptions.mapNotNull { obj ->
            val id = obj.stringOrNull("id") ?: return@mapNotNull null
            val categoriesForFeed = obj["categories"].asArray()
                .mapNotNull { (it as? JsonObject)?.stringOrNull("id") }
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
        val articles = itemsJson["items"].asArray().mapNotNull { value ->
            (value as? JsonObject)?.toArticleOrNull(feedIds)
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

    suspend fun updateSubscriptionTitle(feedId: String, title: String) {
        require(title.isNotBlank()) { "Feed title cannot be blank" }
        post("subscription/edit", mapOf("s" to feedId, "ac" to "edit", "t" to title.trim()))
    }

    /**
     * Subscription folders are represented as labels in the Google Reader API.
     * The endpoint accepts one add/remove label per request, so apply only the
     * difference to avoid removing categories maintained by another client.
     */
    suspend fun updateSubscriptionCategories(feedId: String, currentIds: Set<String>, selectedIds: Set<String>) {
        requireLoggedIn()
        val changes = subscriptionCategoryChanges(currentIds, selectedIds)
        changes.remove.forEach { categoryId ->
            post("subscription/edit", mapOf("s" to feedId, "ac" to "edit", "r" to categoryId))
        }
        changes.add.forEach { categoryId ->
            post("subscription/edit", mapOf("s" to feedId, "ac" to "edit", "a" to categoryId))
        }
    }

    suspend fun createCategoryAndAssign(feedId: String, label: String): String {
        val name = label.trim()
        require(name.isNotBlank()) { "Category name cannot be blank" }
        val id = "user/$username/label/$name"
        post("subscription/edit", mapOf("s" to feedId, "ac" to "edit", "a" to id))
        return id
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
                if (!response.isSuccessful) throw FreshRssException("FreshRSS HTTP ${response.code} at ${request.url.encodedPath}: ${body.take(200)}")
                body
            }
        } catch (e: FreshRssException) {
            throw e
        } catch (e: IOException) {
            throw connectionFailure(request.url.host, e)
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
    private fun JsonElement?.asArray(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

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
        val origin = this["origin"] as? JsonObject
        val feedId = origin?.stringOrNull("streamId")?.takeIf { it in feedIds }
            ?: stringOrNull("crawlTimeMsec")?.let { "feed/unknown" }
            ?: return null
        val author = (this["author"] as? JsonPrimitive)?.contentOrNull
            ?: (this["author"] as? JsonObject)?.stringOrNull("name")
            ?: ""
        val summary = (this["summary"] as? JsonObject)?.stringOrNull("content")
            ?: (this["content"] as? JsonObject)?.stringOrNull("content")
            ?: ""
        val url = this["alternate"].asArray().firstOrNull()?.let { it as? JsonObject }?.string("href")
            ?: origin?.string("htmlUrl") ?: ""
        val timestamp = stringOrNull("crawlTimeMsec")?.toLongOrNull()
            ?: stringOrNull("timestampUsec")?.toLongOrNull()?.div(1000)
            ?: 0L
        val labels = this["categories"].asArray().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
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
