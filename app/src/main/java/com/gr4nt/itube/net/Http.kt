package com.gr4nt.itube.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Simple GET returning the body as text, or null on failure. */
    fun getText(url: String): String? = try {
        client.newCall(
            Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        ).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
    } catch (_: Exception) {
        null
    }
}
