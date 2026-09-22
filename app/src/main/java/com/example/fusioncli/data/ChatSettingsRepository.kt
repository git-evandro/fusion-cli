package com.example.fusioncli.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.chatSettingsDataStore by preferencesDataStore(name = "chat_settings")

/**
 * Persists the selected provider/model and per-provider credentials (API key + base URL).
 */
class ChatSettingsRepository(private val context: Context) {

    private val providerKey = stringPreferencesKey("provider")
    private val modelKey = stringPreferencesKey("model")
    private val providersKey = stringPreferencesKey("providers")

    val settings: Flow<ChatSettings> = context.chatSettingsDataStore.data.map { prefs ->
        ChatSettings(
            provider = prefs[providerKey]?.takeIf { it.isNotBlank() } ?: ChatSettings.DEFAULT_PROVIDER,
            model = prefs[modelKey]?.takeIf { it.isNotBlank() } ?: ChatSettings.DEFAULT_MODEL,
            providers = parseProviders(prefs[providersKey])
        )
    }

    suspend fun updateSelection(provider: String, model: String) {
        context.chatSettingsDataStore.edit {
            it[providerKey] = provider.trim()
            it[modelKey] = model.trim()
        }
    }

    suspend fun updateProvider(provider: String, apiKey: String, baseUrl: String) {
        context.chatSettingsDataStore.edit { prefs ->
            val providers = parseProviders(prefs[providersKey]).toMutableMap()
            providers[provider] = ProviderConfig(
                apiKey = apiKey.trim(),
                baseUrl = baseUrl.trim().ifBlank { ProviderDefaults.baseUrlFor(provider) }
            )
            prefs[providersKey] = serializeProviders(providers)
        }
    }

    private fun parseProviders(json: String?): Map<String, ProviderConfig> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            root.keys().asSequence().associateWith { provider ->
                val entry = root.optJSONObject(provider) ?: JSONObject()
                ProviderConfig(
                    apiKey = entry.optString("apiKey"),
                    baseUrl = entry.optString("baseUrl").ifBlank { ProviderDefaults.baseUrlFor(provider) }
                )
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializeProviders(providers: Map<String, ProviderConfig>): String {
        val root = JSONObject()
        providers.forEach { (provider, config) ->
            root.put(
                provider,
                JSONObject()
                    .put("apiKey", config.apiKey)
                    .put("baseUrl", config.baseUrl)
            )
        }
        return root.toString()
    }
}
