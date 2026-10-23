package com.gr4nt.itube.data

import android.net.Uri
import com.gr4nt.itube.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Segment(
    val startMs: Long,
    val endMs: Long,
    val category: String,
    val uuid: String,
)

enum class SbCategory(val key: String, val label: String, val color: Long) {
    SPONSOR("sponsor", "Sponsor", 0xFF00D400),
    SELF_PROMO("selfpromo", "Self-promotion", 0xFFFFFF00),
    INTERACTION("interaction", "Interaction reminder", 0xFFCC00FF),
    INTRO("intro", "Intro", 0xFF00FFFF),
    OUTRO("outro", "Endcards / credits", 0xFF0202ED),
    PREVIEW("preview", "Preview / recap", 0xFF008FD6),
    MUSIC_OFFTOPIC("music_offtopic", "Non-music section", 0xFFFF9900),
    FILLER("filler", "Filler tangent", 0xFF7300FF);

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key }
    }
}

object SponsorBlock {
    private const val API = "https://sponsor.ajay.app/api"

    suspend fun segments(videoId: String, categories: Set<String>): List<Segment> =
        withContext(Dispatchers.IO) {
            if (categories.isEmpty()) return@withContext emptyList()
            val cats = JSONArray(categories.toList()).toString()
            val url = "$API/skipSegments?videoID=${Uri.encode(videoId)}&categories=${Uri.encode(cats)}"
            val body = Http.getText(url) ?: return@withContext emptyList()
            runCatching {
                val arr = JSONArray(body)
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.getJSONObject(i)
                    if (o.optString("actionType", "skip") != "skip") return@mapNotNull null
                    val seg = o.getJSONArray("segment")
                    Segment(
                        startMs = (seg.getDouble(0) * 1000).toLong(),
                        endMs = (seg.getDouble(1) * 1000).toLong(),
                        category = o.getString("category"),
                        uuid = o.optString("UUID"),
                    )
                }.sortedBy { it.startMs }
            }.getOrDefault(emptyList())
        }
}

/** Return YouTube Dislike community estimates. */
object Dislikes {
    data class Votes(val likes: Long, val dislikes: Long)

    suspend fun votes(videoId: String): Votes? = withContext(Dispatchers.IO) {
        val body = Http.getText("https://returnyoutubedislikeapi.com/votes?videoId=${Uri.encode(videoId)}")
            ?: return@withContext null
        runCatching {
            val o = JSONObject(body)
            Votes(o.optLong("likes", -1), o.optLong("dislikes", -1))
        }.getOrNull()
    }
}
