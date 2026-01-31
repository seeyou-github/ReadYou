package me.ash.reader.infrastructure.translate.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 翻译模型配置
 *
 * @property provider 提供商ID (siliconflow, cerebras)
 * @property model 模型ID (如 "qwen-3-32b")
 * @property apiKey API密钥
 * @property rpm 每分钟请求限制，默认10
 */
@Serializable
data class TranslateModelConfig(
    val provider: String,
    val model: String,
    val apiKey: String,
    val rpm: Int = 10
) {
    companion object {
        fun fromJson(json: String): TranslateModelConfig? {
            return try {
                Json.decodeFromString(serializer(), json)
            } catch (e: Exception) {
                null
            }
        }

        fun toJson(config: TranslateModelConfig): String {
            return Json.encodeToString(serializer(), config)
        }
    }
}

/**
 * 翻译提供商信息
 *
 * @property id 提供商ID
 * @property name 显示名称
 * @property apiUrl API调用地址
 * @property modelsUrl 获取模型列表地址
 * @property description 描述
 */
data class TranslateProviderInfo(
    val id: String,
    val name: String,
    val apiUrl: String,
    val modelsUrl: String,
    val description: String
)

/**
 * 模型信息
 *
 * @property id 模型ID
 * @property name 模型名称（带描述）
 * @property description 模型描述
 * @property isEnabled 是否已启用
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val description: String? = null,
    val isEnabled: Boolean = false
)
