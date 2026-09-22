package com.example.fusioncli.data

data class ProviderConfig(
    val apiKey: String = "",
    val baseUrl: String = ""
)

data class ChatSettings(
    val provider: String = DEFAULT_PROVIDER,
    val model: String = DEFAULT_MODEL,
    val providers: Map<String, ProviderConfig> = emptyMap()
) {
    val currentProvider: String get() = provider
    val currentConfig: ProviderConfig get() = configFor(provider)

    fun configFor(provider: String): ProviderConfig =
        providers[provider] ?: ProviderConfig(baseUrl = ProviderDefaults.baseUrlFor(provider))

    /**
     * The model id to send to the API. Catalog ids look like `deepseek/deepseek-flash`, but a
     * provider called directly expects its own bare name (`deepseek-flash`). The Kilo gateway
     * keeps the `provider/model` form.
     */
    fun requestModelId(): String {
        val base = currentConfig.baseUrl.lowercase()
        return if (base.contains("kilo.ai")) {
            model
        } else {
            model.substringAfter('/')
        }
    }

    companion object {
        const val DEFAULT_PROVIDER = "kilo"
        const val DEFAULT_MODEL = "deepseek/deepseek-v4-flash"
    }
}
