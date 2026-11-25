package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "codeverse_prefs")

data class UserPreferences(
    val language: AppLanguage = AppLanguage.THAI,
    val darkTheme: Boolean = true,
    val editorFontSizeSp: Int = 14,
    val tabSize: Int = 2,
    val sandboxUrl: String = "",
    val sandboxTimeoutMs: Int = 10000,
    val sandboxMemoryMb: Int = 256,
    val shareContextWithAi: Boolean = false
)

class PreferencesRepository(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val FONT_SIZE = intPreferencesKey("editor_font_size")
        val TAB_SIZE = intPreferencesKey("tab_size")
        val SANDBOX_URL = stringPreferencesKey("sandbox_url")
        val SANDBOX_TIMEOUT = intPreferencesKey("sandbox_timeout_ms")
        val SANDBOX_MEMORY = intPreferencesKey("sandbox_memory_mb")
        val SHARE_AI_CONTEXT = booleanPreferencesKey("share_ai_context")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.THAI.code
        UserPreferences(
            language = if (langCode == AppLanguage.ENGLISH.code) AppLanguage.ENGLISH else AppLanguage.THAI,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            editorFontSizeSp = (prefs[Keys.FONT_SIZE] ?: 14).coerceIn(11, 22),
            tabSize = (prefs[Keys.TAB_SIZE] ?: 2).coerceIn(2, 8),
            sandboxUrl = prefs[Keys.SANDBOX_URL] ?: "",
            sandboxTimeoutMs = (prefs[Keys.SANDBOX_TIMEOUT] ?: 10_000).coerceIn(2_000, 30_000),
            sandboxMemoryMb = (prefs[Keys.SANDBOX_MEMORY] ?: 256).coerceIn(64, 1024),
            shareContextWithAi = prefs[Keys.SHARE_AI_CONTEXT] ?: false
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setShareContextWithAi(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHARE_AI_CONTEXT] = enabled }
    }

    suspend fun saveEditorAndSandboxSettings(
        fontSizeSp: Int,
        tabSize: Int,
        sandboxUrl: String,
        timeoutMs: Int,
        memoryMb: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FONT_SIZE] = fontSizeSp.coerceIn(11, 22)
            prefs[Keys.TAB_SIZE] = tabSize.coerceIn(2, 8)
            prefs[Keys.SANDBOX_URL] = sandboxUrl.trim()
            prefs[Keys.SANDBOX_TIMEOUT] = timeoutMs.coerceIn(2_000, 30_000)
            prefs[Keys.SANDBOX_MEMORY] = memoryMb.coerceIn(64, 1024)
        }
    }
}
