package me.ash.reader.infrastructure.translate.transtitle

import me.ash.reader.infrastructure.translate.apistream.StreamTranslateService
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import timber.log.Timber
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

private const val TAG = "TitleTranslateService"

/**
 * 标题翻译服务类
 *
 * 封装标题翻译的具体逻辑，调用 StreamTranslateService 返回翻译结果
 *
 * 创建日期：2026-02-03
 * 修改原因：实现文章标题翻译功能
 * 修改日期：2026-02-03
 * 修改原因：简化解析逻辑，直接使用返回的原文列表；添加取消检查
 */
class TitleTranslateService(
    private val translateService: StreamTranslateService
) {
    /**
     * 翻译标题列表
     *
     * @param titles 待翻译的标题列表
     * @param articleIds 对应的文章 ID 列表（索引与 titles 一一对应）
     * @param config 翻译配置
     * @param translateService 翻译服务（用于取消）
     * @param onProgress 进度回调 (completed, total)
     * @param onError 错误回调
     * @return 文章 ID 到翻译结果的映射（按顺序对应）
     */
    suspend fun translateTitles(
        titles: List<String>,
        articleIds: List<String>,
        config: TranslateModelConfig,
        translateService: StreamTranslateService,
        onProgress: (completed: Int, total: Int) -> Unit,
        onError: (error: Throwable) -> Unit
    ): Map<String, String> {
        Timber.tag(TAG).d("========== 开始翻译标题 ==========")
        Timber.tag(TAG).d("标题数量：${titles.size}")
        Timber.tag(TAG).d("文章数量：${articleIds.size}")

        val result = mutableMapOf<String, String>()

        try {
            // 检查是否已被取消
            coroutineContext.ensureActive()
            
            // 调用 StreamTranslateService.translateStream
            val translatedTexts = translateService.translateStream(
                title = null,  // 标题翻译不需要单独的 title
                texts = titles,
                config = config,
                onNodeCompleted = { nodeId, translatedText ->
                    Timber.tag(TAG).d("节点 $nodeId 翻译完成：$translatedText")
                },
                onProgress = { completed, total ->
                    Timber.tag(TAG).d("翻译进度：$completed / $total")
                    onProgress(completed, total)
                },
                onError = { error ->
                    Timber.tag(TAG).e(error, "翻译错误")
                    onError(error)
                }
            )

            // 再次检查是否已被取消
            coroutineContext.ensureActive()

            // 直接按顺序映射：返回的原文列表按顺序对应到文章ID
            Timber.tag(TAG).d("解析翻译结果，按顺序映射")
            translatedTexts.forEachIndexed { index, translatedTitle ->
                if (index < articleIds.size) {
                    val articleId = articleIds[index]
                    result[articleId] = translatedTitle
                    Timber.tag(TAG).d("文章 $articleId 的标题翻译完成：$translatedTitle")
                } else {
                    Timber.tag(TAG).w("索引 $index 超出文章ID列表范围")
                }
            }

            Timber.tag(TAG).d("========== 翻译完成 ==========")
            Timber.tag(TAG).d("共翻译 ${result.size} 篇文章")
        } catch (e: kotlinx.coroutines.CancellationException) {
            Timber.tag(TAG).d("翻译已被取消")
            translateService.cancel()
            throw e
        }

        return result
    }
}
