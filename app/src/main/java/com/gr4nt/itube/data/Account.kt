package com.gr4nt.itube.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Profile(
    val name: String,
    val handle: String? = null,
    val avatar: String? = null,
    val channelUrl: String? = null,
    val email: String? = null,
)

/** Signed-in YouTube account: the session cookies from the Google sign-in page plus profile info. */
object Account {
    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    private val _cookies = MutableStateFlow<String?>(null)
    val cookies: StateFlow<String?> = _cookies.asStateFlow()

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    val signedIn: Boolean get() = _cookies.value != null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
        _cookies.value = prefs.getString("cookies", null)?.takeIf { sapisid(it) != null }
        _profile.value = prefs.getString("profile", null)?.let { runCatching { json.decodeFromString(Profile.serializer(), it) }.getOrNull() }
    }

    fun signIn(cookieHeader: String) {
        _cookies.value = cookieHeader
        prefs.edit().putString("cookies", cookieHeader).apply()
    }

    fun setProfile(p: Profile) {
        _profile.value = p
        prefs.edit().putString("profile", json.encodeToString(Profile.serializer(), p)).apply()
    }

    fun signOut() {
        _cookies.value = null
        _profile.value = null
        prefs.edit().clear().apply()
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

    fun sapisid(cookies: String): String? =
        cookies.split(";").map { it.trim() }.firstNotNullOfOrNull { c ->
            when {
                c.startsWith("SAPISID=") -> c.substringAfter("=")
                c.startsWith("__Secure-3PAPISID=") -> c.substringAfter("=")
                else -> null
            }
        }?.takeIf { it.isNotBlank() }
}
