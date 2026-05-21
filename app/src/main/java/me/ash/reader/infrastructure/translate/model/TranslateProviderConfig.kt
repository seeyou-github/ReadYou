package me.ash.reader.infrastructure.translate.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 翻译供应商类型
 *
 * - OPENAI: 兼容 OpenAI Chat Completions 协议（含 SiliconFlow / Cerebras 等）
 * - GOOGLE: Google Gemini (generativelanguage)
 * - CLAUDE: Anthropic Claude Messages API
 */
enum class ProviderKind { OPENAI, GOOGLE, CLAUDE }

/** 多 Key 中的单条 Key 配置 */
@Serializable
data class ApiKeyEntry(
    val id: String,
    val key: String,
    val enabled: Boolean = true,
    val favorite: Boolean = false,
)

/** 多 Key 负载均衡策略 */
enum class LoadBalanceStrategy { ROUND_ROBIN, RANDOM }

/**
 * 动态翻译供应商配置
 *
 * 一条记录对应用户在「AI 供应商」页面添加的一个供应商实例。
 */
@Serializable
data class TranslateProviderConfig(
    /** 唯一标识，例如 "OpenAI - 1"；同时作为 Map 的 key */
    val id: String,
    /** 供应商协议族 */
    val kind: ProviderKind = ProviderKind.OPENAI,
    /** 用户可见名称（默认与 id 同步，但允许重命名） */
    val name: String = id,
    /** 是否启用 */
    val enabled: Boolean = true,
    /** 主 / 兼容 API Key（当多 Key 列表为空时使用） */
    val apiKey: String = "",
    /** 多 Key 列表 */
    val keys: List<ApiKeyEntry> = emptyList(),
    /** 多 Key 负载均衡策略 */
    val loadBalance: LoadBalanceStrategy = LoadBalanceStrategy.ROUND_ROBIN,
    /** Base URL */
    val baseUrl: String = "",
    /** OpenAI: chat 路径 */
    val chatPath: String = "/chat/completions",
    /** OpenAI: 是否使用 Responses API */
    val useResponsesApi: Boolean = false,
    /** 每分钟请求上限 */
    val rpm: Int = 10,
    /** 已启用模型 ID 列表 */
    val enabledModels: List<String> = emptyList(),

    // ====== 兼容旧字段（迁移时填入） ======
    /** 兼容旧版的 providerId 字段。新逻辑请使用 id。 */
    val providerId: String = id,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): TranslateProviderConfig? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            null
        }

        fun toJson(config: TranslateProviderConfig): String =
            json.encodeToString(serializer(), config)
    }
}
