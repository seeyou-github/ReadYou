package me.ash.reader.infrastructure.translate.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 翻译提供商配置
 *
 * 存储每个提供商的API配置和启用的模型列表
 *
 * @property providerId 提供商ID (siliconflow, cerebras)
 * @property apiKey API密钥
 * @property rpm 每分钟请求限制，默认10
 * @property enabledModels 启用的模型ID列表
 */
@Serializable
data class TranslateProviderConfig(
    val providerId: String,
    val apiKey: String = "",
    val rpm: Int = 10,
    val enabledModels: List<String> = emptyList()
) {
    companion object {
        fun fromJson(json: String): TranslateProviderConfig? {
            return try {
                Json.decodeFromString(serializer(), json)
            } catch (e: Exception) {
                null
            }
        }

        fun toJson(config: TranslateProviderConfig): String {
            return Json.encodeToString(serializer(), config)
        }
    }
}
