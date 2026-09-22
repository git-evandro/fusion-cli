package com.example.fusioncli.data

/**
 * Default API base URLs for known providers, so each provider can be called directly with its
 * own credentials instead of always going through the Kilo gateway.
 *
 * The names match the provider prefix of the model ids (e.g. `deepseek/deepseek-v4-flash`).
 */
object ProviderDefaults {

    private val urls = mapOf(
        "deepseek" to "https://api.deepseek.com/v1",
        "openai" to "https://api.openai.com/v1",
        "anthropic" to "https://api.anthropic.com/v1",
        "google" to "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
        "mistralai" to "https://api.mistral.ai/v1",
        "x-ai" to "https://api.x.ai/v1",
        "groq" to "https://api.groq.com/openai/v1",
        "openrouter" to "https://openrouter.ai/api/v1",
        "together" to "https://api.together.xyz/v1",
        "cohere" to "https://api.cohere.ai/compatibility/v1",
        "perplexity" to "https://api.perplexity.ai",
        "cerebras" to "https://api.cerebras.ai/v1",
        "fireworks" to "https://api.fireworks.ai/inference/v1",
        "moonshotai" to "https://api.moonshot.ai/v1",
        "z-ai" to "https://api.z.ai/api/paas/v4",
        "qwen" to "https://dashscope.aliyuncs.com/compatible-mode/v1",
        "baidu" to "https://qianfan.baidubce.com/v2",
        "bytedance" to "https://ark.cn-beijing.volces.com/api/v3",
        "kilo" to "https://api.kilo.ai/api/gateway"
    )

    fun baseUrlFor(provider: String): String = urls[provider.lowercase()].orEmpty()
}
