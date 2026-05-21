package me.ash.reader.infrastructure.translate

import android.content.Context
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.infrastructure.translate.model.TranslateProviderInfo
import me.ash.reader.infrastructure.translate.preference.DynamicProvidersPreference

/**
 * 翻译供应商查询入口（向后兼容工具）。
 *
 * 新版基于 [DynamicProvidersPreference] 的动态映射；此对象只提供
 * 一些静态便利方法，方便非 Composable 调用方按 id 反查。
 */
object TranslateProviders {

    /**
     * 同步读取所有动态供应商（DataStore），转成 UI 用的 [TranslateProviderInfo]。
     * 非 Composable 调用方使用；Composable 中应改用 `LocalDynamicProviders.current`。
     */
    fun all(context: Context): List<TranslateProviderInfo> =
        DynamicProvidersPreference.read(context).values.map { it.toInfo() }

    fun getById(context: Context, id: String): TranslateProviderInfo? =
        DynamicProvidersPreference.read(context)[id]?.toInfo()

    /** 给定 cfg 推断默认 modelsUrl（仅用于展示） */
    fun TranslateProviderConfig.toInfo(): TranslateProviderInfo {
        val modelsUrl = when (kind) {
            ProviderKind.OPENAI -> baseUrl.trimEnd('/') + "/models"
            ProviderKind.GOOGLE -> baseUrl.trimEnd('/') + "/models"
            ProviderKind.CLAUDE -> baseUrl.trimEnd('/') + "/models"
        }
        return TranslateProviderInfo(
            id = id,
            name = name,
            apiUrl = baseUrl,
            modelsUrl = modelsUrl,
            description = when (kind) {
                ProviderKind.OPENAI -> "OpenAI 兼容协议"
                ProviderKind.GOOGLE -> "Google Gemini"
                ProviderKind.CLAUDE -> "Anthropic Claude"
            },
        )
    }
}
