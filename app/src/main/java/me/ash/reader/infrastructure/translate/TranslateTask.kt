package me.ash.reader.infrastructure.translate

/**
 * 翻译任务
 *
 * @property id 节点ID，对应 DOM 中的 data-tid 属性
 * @property text 待翻译的原文
 * @property priority 优先级：title=标题，visible=可视区域，normal=普通
 */
data class TranslateTask(
    val id: Int,
    val text: String,
    val priority: TranslatePriority = TranslatePriority.Normal
)

/**
 * 翻译优先级
 */
enum class TranslatePriority {
    /** 标题 */
    Title,

    /** 可视区域 */
    Visible,

    /** 普通 */
    Normal
}

/**
 * 翻译批次
 *
 * @property tasks 该批次包含的所有翻译任务
 * @property isFirstBatch 是否是第一批（包含标题）
 * @property startId 起始节点ID（用于日志）
 * @property endId 结束节点ID（用于日志）
 */
data class TranslationBatch(
    val tasks: List<TranslateTask>,
    val isFirstBatch: Boolean = false,
    val startId: Int = tasks.firstOrNull()?.id ?: -1,
    val endId: Int = tasks.lastOrNull()?.id ?: -1
) {
    /**
     * 该批次的总字符数
     */
    val totalCharacters: Int
        get() = tasks.sumOf { it.text.length }

    /**
     * 该批次的节点数量
     */
    val taskCount: Int
        get() = tasks.size

    /**
     * 翻译完成回调
     */
    var onComplete: ((List<TranslationResult>) -> Unit)? = null

    /**
     * 翻译失败回调
     */
    var onError: ((Throwable) -> Unit)? = null
}

/**
 * 翻译结果
 *
 * @property id 节点ID
 * @property translatedText 翻译后的文本
 */
data class TranslationResult(
    val id: Int,
    val translatedText: String
)
