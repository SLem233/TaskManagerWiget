package ru.slem.taskwidget

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import java.security.SecureRandom

enum class WidgetRefreshState { IDLE, RUNNING, SUCCESS, FAILURE }

object WidgetPreferences {
    const val SETTINGS = "vault_settings"
    const val VAULT_URI = "vault_uri"
    const val VAULT_NAME = "vault_name"
    const val TASK_TAG = "task_tag"
    private const val TOKEN = "widget_action_token"
    private const val REFRESH_STATE = "widget_refresh_state"
    private const val REFRESH_STARTED = "widget_refresh_started"
    private const val REFRESH_FINISHED = "widget_refresh_finished"
    private const val REFRESH_FINISHED_UPTIME = "widget_refresh_finished_uptime"
    const val SUCCESS_VISIBLE_MS = 5_000L
    private const val REFRESH_TIMEOUT_MS = 10L * 60L * 1000L

    fun vaultUri(context: Context): String? =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(VAULT_URI, null)

    fun vaultName(context: Context): String {
        val saved = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
            .getString(VAULT_NAME, null)
        if (!saved.isNullOrBlank()) return saved
        val uri = treeUri(context) ?: return ""
        return runCatching { DocumentsContract.getTreeDocumentId(uri)
            .let(VaultIdentity::nameFromDocumentId) }.getOrDefault("")
    }

    fun tag(context: Context): String =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString(TASK_TAG, "#task") ?: "#task"

    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        prefs.getString(TOKEN, null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val token = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(TOKEN, token).commit()
        return token
    }

    fun validToken(context: Context, candidate: String?): Boolean {
        if (candidate == null) return false
        val expected = token(context).toByteArray(Charsets.UTF_8)
        val actual = candidate.toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.isEqual(expected, actual)
    }

    fun fontSize(context: Context, widgetId: Int): Float =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getInt("font_$widgetId", 15).toFloat()

    fun opacity(context: Context, widgetId: Int): Int =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getInt("opacity_$widgetId", 80)
            .coerceIn(0, 100)

    fun saveAppearance(context: Context, widgetId: Int, font: Int, opacity: Int) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            .putInt("font_$widgetId", font.coerceIn(12, 22))
            .putInt("opacity_$widgetId", opacity.coerceIn(0, 100)).apply()
    }

    fun lastScanError(context: Context): String? =
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).getString("last_scan_error", null)

    fun setScanError(context: Context, error: String?) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            .putString("last_scan_error", error).apply()
    }

    fun refreshState(
        context: Context, nowMillis: Long = System.currentTimeMillis(),
        nowUptime: Long = SystemClock.uptimeMillis()
    ): WidgetRefreshState {
        val prefs = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE)
        val saved = prefs.getString(REFRESH_STATE, WidgetRefreshState.IDLE.name)
        val state = WidgetRefreshState.entries.firstOrNull { it.name == saved }
            ?: WidgetRefreshState.IDLE
        val started = prefs.getLong(REFRESH_STARTED, 0)
        val finished = prefs.getLong(REFRESH_FINISHED, -1)
        val finishedUptime = prefs.getLong(REFRESH_FINISHED_UPTIME, -1)
        val elapsedUptime = nowUptime - finishedUptime
        return when {
            state == WidgetRefreshState.RUNNING &&
                (started <= 0 || nowMillis - started > REFRESH_TIMEOUT_MS) -> WidgetRefreshState.FAILURE
            state == WidgetRefreshState.SUCCESS && finishedUptime >= 0 &&
                (elapsedUptime < 0 || elapsedUptime >= SUCCESS_VISIBLE_MS) -> WidgetRefreshState.IDLE
            state == WidgetRefreshState.SUCCESS &&
                finished >= 0 && nowMillis - finished >= SUCCESS_VISIBLE_MS -> WidgetRefreshState.IDLE
            else -> state
        }
    }

    fun setRefreshState(context: Context, state: WidgetRefreshState) {
        context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE).edit()
            .putString(REFRESH_STATE, state.name)
            .putLong(REFRESH_STARTED, if (state == WidgetRefreshState.RUNNING)
                System.currentTimeMillis() else 0)
            .putLong(REFRESH_FINISHED, if (state == WidgetRefreshState.SUCCESS)
                System.currentTimeMillis() else -1)
            .putLong(REFRESH_FINISHED_UPTIME, if (state == WidgetRefreshState.SUCCESS)
                SystemClock.uptimeMillis() else -1)
            .apply()
    }

    fun treeUri(context: Context): Uri? = vaultUri(context)?.let(Uri::parse)
}