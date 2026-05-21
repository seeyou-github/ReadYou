package me.ash.reader.infrastructure.translate.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 翻译模型配置
 *
 * 一旦用户在「翻译模型」里选定了某个供应商下的某个模型，本对象就承载
 * 接下来发请求所需的全部上下文（含 baseUrl/kind 等），避免每次再去查询动态供应商存储。
 */
@Serializable
data class TranslateModelConfig(
    val provider: String,            // 供应商 id（动态存储的 key）
    val model: String,
    val apiKey: String,
    val rpm: Int = 10,
    val kind: ProviderKind = ProviderKind.OPENAI,
    val baseUrl: String = "",        // 例如 https://api.siliconflow.cn/v1
    val chatPath: String = "/chat/completions",
    val useResponsesApi: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun fromJson(text: String): TranslateModelConfig? = try {
            json.decodeFromString(serializer(), text)
        } catch (_: Exception) {
            null
        }
        fun toJson(config: TranslateModelConfig): String =
            json.encodeToString(serializer(), config)
    }
}

/**
 * 翻译提供商展示信息（运行期 UI 辅助类型）
 */
data class TranslateProviderInfo(
    val id: String,
    val name: String,
    val apiUrl: String,
    val modelsUrl: String,
    val description: String,
)

/**
 * 模型信息
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val isEnabled: Boolean = false,
)
