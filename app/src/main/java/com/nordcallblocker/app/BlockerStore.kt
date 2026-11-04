package com.nordcallblocker.app

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import org.json.JSONArray
import org.json.JSONObject

internal object BlockerStore {
    private const val PREFS = "blocker_settings"
    private const val KEY_ENABLED = "enabled"
    private const val HISTORY = "blocked_history"
    private const val MAX_ENTRIES = 200

    fun isUserUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked == true

    fun isEnabledSettingInitialized(context: Context): Boolean =
        devicePreferences(context).contains(KEY_ENABLED)

    /**
     * Reads the screening switch from device-encrypted storage.
     *
     * Existing installations used credential-encrypted preferences. The first read while the
     * user is unlocked copies that effective value to device-encrypted storage and removes only
     * the obsolete CE key. If no DE value exists during Direct Boot, fail open: blocking a call
     * without a known user setting is less safe than allowing it.
     */
    @Synchronized
    fun enabled(context: Context): Boolean {
        val devicePrefs = devicePreferences(context)
        if (devicePrefs.contains(KEY_ENABLED)) {
            return devicePrefs.getBoolean(KEY_ENABLED, false)
        }

        if (!isUserUnlocked(context)) return false

        val credentialPrefs = credentialPreferences(context)
        val migratedValue = credentialPrefs.getBoolean(KEY_ENABLED, true)
        if (devicePrefs.edit().putBoolean(KEY_ENABLED, migratedValue).commit()) {
            // Keep the history in CE; remove only the setting that has moved to DE.
            credentialPrefs.edit().remove(KEY_ENABLED).apply()
        } else {
            AppLog.w(context, "Unable to initialize device-protected blocker setting")
        }
        return migratedValue
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        val saved = devicePreferences(context).edit().putBoolean(KEY_ENABLED, enabled).commit()
        if (!saved) AppLog.w(context, "Unable to persist device-protected blocker setting")
        return saved
    }

    @Synchronized
    fun append(context: Context, number: String): Boolean {
        if (!isUserUnlocked(context)) return false

        val prefs = credentialPreferences(context)
        val old = try { JSONArray(prefs.getString(HISTORY, "[]")) } catch (_: Exception) { JSONArray() }
        val updated = JSONArray()
        updated.put(JSONObject().put("number", number).put("timestamp", System.currentTimeMillis()))
        for (index in 0 until minOf(old.length(), MAX_ENTRIES - 1)) updated.put(old.get(index))
        // The screening response is sent before this method is called. commit() ensures the
        // bounded history is on disk before Telecom can release the service process.
        return prefs.edit().putString(HISTORY, updated.toString()).commit()
    }

    @Synchronized
    fun history(context: Context): List<Pair<String, Long>> {
        if (!isUserUnlocked(context)) return emptyList()

        val text = credentialPreferences(context).getString(HISTORY, "[]")
        val array = try { JSONArray(text) } catch (_: Exception) { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            entry.optString("number", "Desconocido") to entry.optLong("timestamp")
        }
    }

    @Synchronized
    fun clear(context: Context): Boolean {
        if (!isUserUnlocked(context)) return false
        return credentialPreferences(context).edit().remove(HISTORY).commit()
    }

    private fun devicePreferences(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun credentialPreferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
