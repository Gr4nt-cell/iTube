package com.gr4nt.itube.net.potoken

// Ported from NewPipe (GPL-3.0): converts BotGuard data between YouTube's wire format and JavaScript.

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class PoTokenException(message: String) : Exception(message)
class BadWebViewException(message: String) : Exception(message)

internal fun parseChallengeData(rawChallengeData: String): String {
    val scrambled = JSONArray(rawChallengeData)
    val challengeData = if (scrambled.length() > 1 && scrambled.opt(1) is String) {
        JSONArray(descramble(scrambled.getString(1)))
    } else {
        scrambled.getJSONArray(0)
    }

    val messageId = challengeData.getString(0)
    val interpreterHash = challengeData.getString(3)
    val program = challengeData.getString(4)
    val globalName = challengeData.getString(5)
    val clientExperimentsStateBlob = challengeData.optString(7)

    fun firstString(index: Int): String? {
        val arr = challengeData.optJSONArray(index) ?: return null
        for (i in 0 until arr.length()) {
            val v = arr.opt(i)
            if (v is String) return v
        }
        return null
    }

    val interpreter = JSONObject()
        .put("privateDoNotAccessOrElseSafeScriptWrappedValue", firstString(1) ?: JSONObject.NULL)
        .put("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", firstString(2) ?: JSONObject.NULL)

    return JSONObject()
        .put("messageId", messageId)
        .put("interpreterJavascript", interpreter)
        .put("interpreterHash", interpreterHash)
        .put("program", program)
        .put("globalName", globalName)
        .put("clientExperimentsStateBlob", clientExperimentsStateBlob)
        .toString()
}

internal fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
    val arr = JSONArray(raw)
    return newUint8Array(base64ToBytes(arr.getString(0))) to arr.getLong(1)
}

internal fun stringToU8(identifier: String): String = newUint8Array(identifier.toByteArray())

internal fun u8ToBase64(poToken: String): String {
    val bytes = poToken.split(",").map { it.trim().toInt().toByte() }.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
        .replace("+", "-")
        .replace("/", "_")
}

private fun descramble(scrambled: String): String =
    base64ToBytes(scrambled).map { (it + 97).toByte() }.toByteArray().decodeToString()

private fun newUint8Array(contents: ByteArray): String =
    "new Uint8Array([" + contents.joinToString(",") { (it.toInt() and 0xFF).toString() } + "])"

private fun base64ToBytes(base64: String): ByteArray {
    val fixed = base64.replace('-', '+').replace('_', '/').replace('.', '=')
    return try {
        Base64.decode(fixed, Base64.DEFAULT)
    } catch (e: IllegalArgumentException) {
        throw PoTokenException("Cannot base64 decode")
    }
}

internal fun exceptionForJsError(error: String): Exception =
    if (error.contains("SyntaxError")) BadWebViewException(error) else PoTokenException(error)
