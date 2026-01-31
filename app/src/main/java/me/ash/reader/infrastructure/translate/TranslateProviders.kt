package me.ash.reader.infrastructure.translate

import me.ash.reader.infrastructure.translate.model.TranslateProviderInfo

/**
 * 翻译提供商常量
 *
 * 包含 SiliconFlow 和 Cerebras 的提供商信息
 */
object TranslateProviders {

    /**
     * SiliconFlow 提供商
     * 提供 GLM-4 等模型
     */
    val SILICONFLOW = TranslateProviderInfo(
        id = "siliconflow",
        name = "SiliconFlow",
        apiUrl = "https://api.siliconflow.cn/v1/chat/completions",
        modelsUrl = "https://api.siliconflow.cn/v1/models",
        description = "提供 GLM-4 等高性能模型"
    )

    /**
     * Cerebras 提供商
     * 提供 qwen-3-32b 等模型
     */
    val CEREBRAS = TranslateProviderInfo(
        id = "cerebras",
        name = "Cerebras",
        apiUrl = "https://api.cerebras.ai/v1/chat/completions",
        modelsUrl = "https://api.cerebras.ai/v1/models",
        description = "提供 qwen-3-32b 等优质模型"
    )

    /**
     * 所有可用提供商列表
     */
    val ALL = listOf(SILICONFLOW, CEREBRAS)

    /**
     * 根据ID获取提供商信息
     *
     * @param id 提供商ID
     * @return 提供商信息，找不到返回null
     */
    fun getById(id: String): TranslateProviderInfo? {
        return ALL.find { it.id == id }
    }
}
