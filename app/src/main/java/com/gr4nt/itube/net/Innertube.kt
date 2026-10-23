package com.gr4nt.itube.net

import com.gr4nt.itube.data.Account
import com.gr4nt.itube.data.ChannelItem
import com.gr4nt.itube.data.Item
import com.gr4nt.itube.data.PageResult
import com.gr4nt.itube.data.PlaylistItem
import com.gr4nt.itube.data.Profile
import com.gr4nt.itube.data.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import java.security.MessageDigest
import java.util.Locale

/**
 * Minimal authenticated client for YouTube's InnerTube API, used for signed-in features:
 * profile, recommendations, subscriptions feed, history, Watch later, liked videos and
 * the list of subscribed channels.
 */
object Innertube {
    private const val ORIGIN = "https://www.youtube.com"
    private const val FALLBACK_VERSION = "2.20261020.01.00"

    @Volatile
    private var clientVersion: String? = null

    private fun version(): String = clientVersion ?: runCatching { YoutubeParsingHelper.getClientVersion() }
        .getOrNull().also { clientVersion = it } ?: FALLBACK_VERSION

    private fun authHeader(cookies: String): String? {
        val sapisid = Account.sapisid(cookies) ?: return null
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1").digest("$ts $sapisid $ORIGIN".toByteArray())
        val hash = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$hash"
    }

    private fun context(): JSONObject {
        val locale = Locale.getDefault()
        return JSONObject().put(
            "client",
            JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", version())
                .put("hl", locale.language.ifBlank { "en" })
                .put("gl", locale.country.ifBlank { "US" }),
        )
    }

    private suspend fun post(endpoint: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val cookies = Account.cookies.value ?: error("Not signed in")
        body.put("context", context())
        val req = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("User-Agent", Http.USER_AGENT)
            .header("Cookie", cookies)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("Referer", "$ORIGIN/")
            .header("X-Goog-AuthUser", "0")
            .header("X-Youtube-Client-Name", "1")
            .header("X-Youtube-Client-Version", version())
            .apply { authHeader(cookies)?.let { header("Authorization", it) } }
            .build()
        Http.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (r.code == 401 || r.code == 403) error("Your YouTube session expired — sign in again")
            if (!r.isSuccessful) error("YouTube returned HTTP ${r.code}")
            JSONObject(text)
        }
    }

    // ---------------- Profile ----------------

    suspend fun fetchProfile(): Profile {
        val res = post("account/account_menu", JSONObject())
        val header = findFirst(res, "activeAccountHeaderRenderer") ?: error("Could not read your account")
        val name = text(header.opt("accountName")) ?: "YouTube user"
        val handle = text(header.opt("channelHandle"))
        val email = text(header.opt("email"))
        val avatar = thumbs(header.optJSONObject("accountPhoto"))
        val channelId = findFirst(res, "browseEndpoint")?.optString("browseId")?.takeIf { it.startsWith("UC") }
        return Profile(
            name = name,
            handle = handle,
            avatar = avatar,
            email = email,
            channelUrl = channelId?.let { "https://www.youtube.com/channel/$it" },
        )
    }

    // ---------------- Feeds ----------------

    private suspend fun browse(browseId: String?, continuation: String? = null): JSONObject {
        val body = JSONObject()
        if (continuation != null) body.put("continuation", continuation) else body.put("browseId", browseId)
        return post("browse", body)
    }

    private suspend fun pagedBrowse(browseId: String, continuation: String? = null): PageResult<Item> {
        val res = browse(browseId, continuation)
        val items = parseItems(res)
        val token = findContinuation(res)
        val next: (suspend () -> PageResult<Item>)? = token?.let { t -> suspend { pagedBrowse(browseId, t) } }
        return PageResult(items, next)
    }

    suspend fun recommendations() = pagedBrowse("FEwhat_to_watch")
    suspend fun subscriptionFeed() = pagedBrowse("FEsubscriptions")
    suspend fun history() = pagedBrowse("FEhistory")
    suspend fun watchLater() = pagedBrowse("VLWL")
    suspend fun liked() = pagedBrowse("VLLL")

    /** All channels the account is subscribed to (follows continuations). */
    suspend fun subscribedChannels(): List<ChannelItem> {
        val out = mutableListOf<ChannelItem>()
        var res = browse("FEchannels")
        repeat(30) {
            out += parseItems(res).filterIsInstance<ChannelItem>()
            val token = findContinuation(res) ?: return out.distinctBy { it.url }
            res = browse(null, token)
        }
        return out.distinctBy { it.url }
    }

    // ---------------- Parsing ----------------

    private fun text(o: Any?): String? = when (o) {
        is JSONObject -> o.optString("simpleText").takeIf { it.isNotEmpty() }
            ?: o.optJSONArray("runs")?.let { runs -> (0 until runs.length()).joinToString("") { runs.getJSONObject(it).optString("text") } }
            ?: o.optString("content").takeIf { it.isNotEmpty() }
        is String -> o
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun thumbs(o: JSONObject?): String? {
        val arr = o?.optJSONArray("thumbnails") ?: o?.optJSONArray("sources") ?: return null
        if (arr.length() == 0) return null
        val url = arr.getJSONObject(arr.length() - 1).optString("url")
        return if (url.startsWith("//")) "https:$url" else url
    }

    private fun findFirst(node: Any?, key: String): JSONObject? {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let { return it }
                for (k in node.keys()) findFirst(node.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findFirst(node.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun findContinuation(res: JSONObject): String? {
        var token: String? = null
        walk(res) { key, value ->
            if (token == null && key == "continuationCommand" && value is JSONObject) {
                token = value.optString("token").takeIf { it.isNotEmpty() }
            }
            if (token == null && key == "nextContinuationData" && value is JSONObject) {
                token = value.optString("continuation").takeIf { it.isNotEmpty() }
            }
        }
        return token
    }

    private fun walk(node: Any?, visit: (String, Any?) -> Unit) {
        when (node) {
            is JSONObject -> for (k in node.keys()) {
                val v = node.opt(k)
                visit(k, v)
                walk(v, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private fun parseItems(res: JSONObject): List<Item> {
        val items = mutableListOf<Item>()
        val seen = HashSet<String>()
        fun add(item: Item?) {
            if (item != null && seen.add(item.url)) items += item
        }
        walk(res) { key, value ->
            if (value !is JSONObject) return@walk
            when (key) {
                "videoRenderer", "gridVideoRenderer", "playlistVideoRenderer", "compactVideoRenderer" -> add(video(value))
                "lockupViewModel" -> add(lockup(value))
                "reelItemRenderer" -> add(reel(value))
                "channelRenderer", "gridChannelRenderer" -> add(channel(value))
                "playlistRenderer", "gridPlaylistRenderer" -> add(playlist(value))
            }
        }
        return items
    }

    private fun parseCount(s: String?): Long {
        if (s.isNullOrBlank()) return -1
        val m = Regex("([\\d.,]+)\\s*([KMBkmb]?)").find(s) ?: return -1
        val num = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return -1
        val mult = when (m.groupValues[2].uppercase()) { "K" -> 1e3; "M" -> 1e6; "B" -> 1e9; else -> 1.0 }
        return (num * mult).toLong()
    }

    private fun parseDuration(s: String?): Long {
        if (s.isNullOrBlank()) return -1
        val parts = s.trim().split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return -1
        return parts.fold(0L) { acc, p -> acc * 60 + p }
    }

    private fun ownerUrl(o: JSONObject): String? {
        val runs = (o.optJSONObject("ownerText") ?: o.optJSONObject("shortBylineText") ?: o.optJSONObject("longBylineText"))
            ?.optJSONArray("runs") ?: return null
        val ep = runs.optJSONObject(0)?.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return null
        val id = ep.optString("browseId")
        return if (id.startsWith("UC")) "https://www.youtube.com/channel/$id" else null
    }

    private fun video(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        val badges = o.optJSONArray("badges")?.toString().orEmpty()
        val length = text(o.opt("lengthText"))
            ?: findFirst(o, "thumbnailOverlayTimeStatusRenderer")?.let { text(it.opt("text")) }
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = text(o.opt("title")) ?: return null,
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            uploader = text(o.opt("ownerText")) ?: text(o.opt("shortBylineText")) ?: text(o.opt("longBylineText")) ?: "",
            uploaderUrl = ownerUrl(o),
            uploaderAvatar = findFirst(o, "channelThumbnailSupportedRenderers")?.let { thumbs(findFirst(it, "thumbnail")) },
            durationSec = parseDuration(length),
            views = parseCount(text(o.opt("viewCountText")) ?: text(o.opt("shortViewCountText"))),
            uploaded = text(o.opt("publishedTimeText")),
            isLive = badges.contains("LIVE") || length.equals("LIVE", true),
        )
    }

    /** New-style "lockup" cards used on home and subscriptions. */
    private fun lockup(o: JSONObject): Item? {
        val id = o.optString("contentId").takeIf { it.isNotEmpty() } ?: return null
        val type = o.optString("contentType")
        val meta = findFirst(o, "lockupMetadataViewModel") ?: return null
        val title = text(meta.opt("title")) ?: return null
        val rows = mutableListOf<String>()
        findFirst(meta, "contentMetadataViewModel")?.optJSONArray("metadataRows")?.let { mr ->
            for (i in 0 until mr.length()) {
                val parts = mr.getJSONObject(i).optJSONArray("metadataParts") ?: continue
                for (j in 0 until parts.length()) text(parts.getJSONObject(j).opt("text"))?.let(rows::add)
            }
        }
        val badge = findFirst(o, "thumbnailBadgeViewModel")?.optString("text")
        val avatar = findFirst(meta, "avatarViewModel")?.let { thumbs(findFirst(it, "image")) }
        var channelUrl: String? = null
        walk(meta) { k, v ->
            if (channelUrl == null && k == "browseEndpoint" && v is JSONObject) {
                val bid = v.optString("browseId")
                if (bid.startsWith("UC")) channelUrl = "https://www.youtube.com/channel/$bid"
            }
        }
        return when (type) {
            "LOCKUP_CONTENT_TYPE_PLAYLIST", "LOCKUP_CONTENT_TYPE_PODCAST" -> PlaylistItem(
                url = "https://www.youtube.com/playlist?list=$id",
                title = title,
                thumbnail = thumbs(findFirst(o, "image")),
                uploader = rows.firstOrNull() ?: "",
                count = parseCount(badge),
            )
            else -> VideoItem(
                url = "https://www.youtube.com/watch?v=$id",
                title = title,
                thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                uploader = rows.getOrNull(0) ?: "",
                uploaderUrl = channelUrl,
                uploaderAvatar = avatar,
                durationSec = parseDuration(badge),
                views = parseCount(rows.firstOrNull { it.contains("view", true) }),
                uploaded = rows.firstOrNull { it.contains("ago", true) },
                isLive = badge.equals("LIVE", true),
            )
        }
    }

    private fun reel(o: JSONObject): VideoItem? {
        val id = o.optString("videoId").takeIf { it.isNotEmpty() } ?: return null
        return VideoItem(
            url = "https://www.youtube.com/watch?v=$id",
            title = text(o.opt("headline")) ?: return null,
            thumbnail = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            views = parseCount(text(o.opt("viewCountText"))),
            isShort = true,
        )
    }

    private fun channel(o: JSONObject): ChannelItem? {
        val id = o.optString("channelId").takeIf { it.startsWith("UC") } ?: return null
        return ChannelItem(
            url = "https://www.youtube.com/channel/$id",
            title = text(o.opt("title")) ?: return null,
            thumbnail = thumbs(o.optJSONObject("thumbnail")),
            subscribers = parseCount(text(o.opt("subscriberCountText")) ?: text(o.opt("videoCountText"))),
        )
    }

    private fun playlist(o: JSONObject): PlaylistItem? {
        val id = o.optString("playlistId").takeIf { it.isNotEmpty() } ?: return null
        return PlaylistItem(
            url = "https://www.youtube.com/playlist?list=$id",
            title = text(o.opt("title")) ?: return null,
            thumbnail = thumbs(findFirst(o, "thumbnail")),
            uploader = text(o.opt("shortBylineText")) ?: "",
            count = parseCount(text(o.opt("videoCount")) ?: text(o.opt("videoCountText"))),
        )
    }
}
