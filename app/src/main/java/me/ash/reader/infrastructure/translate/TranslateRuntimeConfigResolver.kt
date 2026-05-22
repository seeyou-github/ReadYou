package me.ash.reader.infrastructure.translate

import android.content.Context
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.preference.DynamicProvidersPreference

object TranslateRuntimeConfigResolver {
    fun resolve(context: Context, config: TranslateModelConfig): TranslateModelConfig {
        val provider = readProvider(context, config.provider) ?: return config
        val apiKey = KeyPicker.pick(provider)
        return config.copy(
            apiKey = apiKey.ifBlank { config.apiKey },
            rpm = provider.rpm,
            kind = provider.kind,
            baseUrl = provider.baseUrl.ifBlank { config.baseUrl },
            chatPath = provider.chatPath.ifBlank { config.chatPath },
            useResponsesApi = provider.useResponsesApi,
        )
    }

    private fun readProvider(context: Context, providerId: String): TranslateProviderConfig? {
        val normalizedProviderId = when (providerId.lowercase()) {
            "siliconflow" -> "SiliconFlow"
            "cerebras" -> "Cerebras"
            else -> providerId
        }
        val persisted = DynamicProvidersPreference.read(context)[providerId]
            ?: DynamicProvidersPreference.read(context)[normalizedProviderId]
        if (persisted != null) return persisted
        val hidden = DynamicProvidersPreference.readHiddenBuiltIns(context)
        if (providerId in hidden || normalizedProviderId in hidden) return null
        return DynamicProvidersPreference.builtInConfig(providerId)
            ?: DynamicProvidersPreference.builtInConfig(normalizedProviderId)
    }
}
