package me.ash.reader.infrastructure.translate.transtitle

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.translate.TranslateProvider
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateServiceFactory
import me.ash.reader.infrastructure.translate.cache.ArticleTranslationCacheService
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import java.util.regex.Pattern

private const val TAG = "TitleTranslateEntry"

/**
 * 标题翻译入口类
 *
 * 负责标题翻译的触发、执行、缓存更新和 UI 通知
 *
 * 创建日期：2026-02-03
 * 修改原因：实现文章标题翻译功能
 */
@Singleton
class TitleTranslateEntry @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val translationCacheService: ArticleTranslationCacheService,
    private val streamTranslateServiceFactory: StreamTranslateServiceFactory,
    private val settingsProvider: SettingsProvider,
    private val titleTranslateQueue: TitleTranslateQueue,
) {
    // 翻译状态
    val isTranslating = mutableStateOf(false)
    val translationProgress = mutableStateOf(0)
    val translationTotal = mutableStateOf(0)
    val translationError = MutableStateFlow<Throwable?>(null)

    // 当前正在翻译的 Feed ID
    private var currentFeedId: String? = null

    // 防抖时间（毫秒）
    private val debounceTime = 500L

    // 上次触发时间
    private var lastTriggerTime = 0L

    // 当前正在处理的 Feed ID（用于防重复）
    private var translatingFeedId: String? = null

    // 当前翻译任务的 Job
    private var currentTranslationJob: kotlinx.coroutines.Job? = null

    /**
     * 触发标题翻译
     *
     * @param feedId Feed ID
     * @param triggerSource 触发来源（用于日志）
     */
    suspend fun triggerTranslation(feedId: String, triggerSource: String = "unknown") = withContext(ioDispatcher) {
        Timber.tag(TAG).d("========== 触发标题翻译 ==========")
        Timber.tag(TAG).d("Feed ID = $feedId, 触发来源 = $triggerSource")

        // 检查是否正在翻译相同的 Feed
        if (translatingFeedId == feedId) {
            Timber.tag(TAG).d("Feed $feedId 正在翻译中，跳过此次触发，触发来源：$triggerSource")
            return@withContext
        }

        // 防抖检查
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastTriggerTime < debounceTime && translatingFeedId != null) {
            Timber.tag(TAG).d("距离上次触发不足 ${debounceTime}ms，跳过此次触发，触发来源：$triggerSource")
            return@withContext
        }

        lastTriggerTime = currentTime

        currentFeedId = feedId

        // 步骤 1: 检查 Feed 是否开启自动翻译标题
        val feed = feedDao.queryById(feedId)
        Timber.tag(TAG).d("步骤 1: 检查 Feed 设置，Feed.isAutoTranslateTitle = ${feed?.isAutoTranslateTitle}")

        if (feed == null || !feed.isAutoTranslateTitle) {
            Timber.tag(TAG).w("Feed 未开启自动翻译标题，取消翻译")
            return@withContext
        }

        // 步骤 2: 查找需要翻译的文章
        val articlesToTranslate = findArticlesNeedingTranslation(feedId, feed.accountId)
        Timber.tag(TAG).d("步骤 2: 找到 ${articlesToTranslate.size} 篇需要翻译的文章")

        if (articlesToTranslate.isEmpty()) {
            Timber.tag(TAG).d("没有需要翻译的文章，翻译流程结束")
            return@withContext
        }

        // 步骤 3: 尝试添加到翻译队列
        val enqueued = titleTranslateQueue.enqueueTask(feedId, feed.name, triggerSource)
        if (!enqueued) {
            Timber.tag(TAG).d("Feed ${feed.name} ($feedId) 已在队列或正在处理中，跳过此次触发")
            return@withContext
        }

        // 步骤 4: 执行翻译
        Timber.tag(TAG).d("步骤 4: 开始翻译 ${articlesToTranslate.size} 个标题")
        translationTotal.value = articlesToTranslate.size
        translationProgress.value = 0
        isTranslating.value = true
        translatingFeedId = feedId

        try {
            performTranslation(articlesToTranslate, feed)
            Timber.tag(TAG).d("========== 标题翻译完成 ==========")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "标题翻译失败")
            translationError.value = e
        } finally {
            isTranslating.value = false
            translatingFeedId = null
        }
    }

    /**
     * 查找需要翻译的文章
     *
     * @param feedId Feed ID
     * @param accountId 账户 ID
     * @return 需要翻译的文章列表
     */
    private suspend fun findArticlesNeedingTranslation(
        feedId: String,
        accountId: Int
    ): List<Article> {
        // 查询 Feed 下的所有文章
        val articles = articleDao.queryAllByFeedId(accountId, feedId)
        Timber.tag(TAG).d("Feed 下共有 ${articles.size} 篇文章")

        // 筛选需要翻译的文章：
        // 1. translatedTitle 为空（未翻译）
        // 2. 标题需要翻译（存在英文字符，不存在中文字符）
        return articles.filter { article ->
            val isNotTranslated = article.translatedTitle.isNullOrBlank()
            val needsTranslation = needsTranslation(article.title)

            Timber.tag(TAG).v("文章 ID = ${article.id}, 标题 = ${article.title}, 未翻译 = $isNotTranslated, 需要翻译 = $needsTranslation")

            isNotTranslated && needsTranslation
        }
    }

    /**
     * 判断标题是否需要翻译
     *
     * 翻译条件：
     * - 存在中文字符 → 不翻译
     * - 不存在英文字符 → 不翻译
     * - 其他 → 翻译
     *
     * @param title 标题
     * @return 是否需要翻译
     */
    private fun needsTranslation(title: String): Boolean {
        Timber.tag(TAG).v("检查标题是否需要翻译：$title")

        // 检查是否包含中文字符
        val hasChinese = Pattern.compile("[\u4e00-\u9fa5]").matcher(title).find()
        Timber.tag(TAG).v("包含中文字符 = $hasChinese")

        if (hasChinese) {
            Timber.tag(TAG).v("包含中文字符，不需要翻译")
            return false
        }

        // 检查是否包含英文字符
        val hasEnglish = Pattern.compile("[a-zA-Z]").matcher(title).find()
        Timber.tag(TAG).v("包含英文字符 = $hasEnglish")

        if (!hasEnglish) {
            Timber.tag(TAG).v("不包含英文字符，不需要翻译")
            return false
        }

        Timber.tag(TAG).v("需要翻译")
        return true
    }

    /**
     * 执行翻译
     *
     * @param articles 需要翻译的文章列表
     * @param Feed Feed 信息
     */
    private suspend fun performTranslation(articles: List<Article>, feed: Feed) = kotlinx.coroutines.coroutineScope {
        // 设置当前翻译任务的 Job
        currentTranslationJob = coroutineContext[Job]
        
        // 提取需要翻译的标题
        val titles = articles.map { it.title }
        Timber.tag(TAG).d("待翻译的标题列表：$titles")

        // 构建 ID 到标题的映射（用于结果关联）
        val articleIdToTitleMap = articles.associate { it.id to it.title }
        Timber.tag(TAG).d("文章 ID 到标题的映射：$articleIdToTitleMap")

        // 获取翻译配置（从设置中读取）
        val config = settingsProvider.settings.quickTranslateModel ?: TranslateModelConfig(
            provider = TranslateProvider.SILICONFLOW.serviceId,
            model = "",
            apiKey = ""
        )
        Timber.tag(TAG).d("翻译配置：provider = ${config.provider}, model = ${config.model}")

        // 获取翻译服务
        val translateService = streamTranslateServiceFactory.getService(config.provider)
        Timber.tag(TAG).d("使用翻译服务：${translateService.getServiceName()}")

        // 创建 TitleTranslateService
        val titleTranslateService = TitleTranslateService(translateService)

        // 检查是否已被取消
        ensureActive()
        Timber.tag(TAG).d("开始翻译任务")

        // 执行翻译
        val results = titleTranslateService.translateTitles(
            titles = titles,
            articleIds = articles.map { it.id },
            config = config,
            translateService = translateService,
            onProgress = { completed, total ->
                translationProgress.value = completed
                Timber.tag(TAG).d("翻译进度：$completed / $total")
            },
            onError = { error ->
                Timber.tag(TAG).e(error, "翻译过程中发生错误")
                translationError.value = error
            }
        )

        // 更新缓存和 diffMap
        Timber.tag(TAG).d("更新缓存和数据库")
        results.forEach { (articleId, translatedTitle) ->
            updateCacheAndDatabase(articleId, translatedTitle, feed, config)
        }
    }

    /**
     * 更新缓存和数据库
     *
     * @param articleId 文章 ID
     * @param translatedTitle 翻译后的标题
     * @param feed Feed 信息
     * @param config 翻译配置
     */
    private suspend fun updateCacheAndDatabase(articleId: String, translatedTitle: String, feed: Feed, config: TranslateModelConfig) {
        Timber.tag(TAG).d("更新缓存和数据库，articleId = $articleId, translatedTitle = $translatedTitle")

        // 更新数据库
        articleDao.updateTranslatedTitle(articleId, translatedTitle)
        Timber.tag(TAG).d("数据库更新完成")

        // 仅更新标题译文，避免覆盖正文翻译缓存
        translationCacheService.updateTitleOnly(
            articleId = articleId,
            translatedTitle = translatedTitle,
            provider = config.provider,
            model = config.model,
        )
        Timber.tag(TAG).d("标题译文缓存更新完成")
    }

    /**
     * 取消指定 Feed 的翻译
     *
     * @param feedId Feed ID
     */
    fun cancelTranslation(feedId: String) {
        Timber.tag(TAG).d("取消 Feed $feedId 的翻译任务")
        
        if (translatingFeedId == feedId) {
            currentTranslationJob?.cancel()
            currentTranslationJob = null
            isTranslating.value = false
            Timber.tag(TAG).d("已取消 Feed $feedId 的翻译任务")
        }
    }

    /**
     * 取消所有翻译
     */
    fun cancelAllTranslations() {
        Timber.tag(TAG).d("取消所有翻译任务")
        currentTranslationJob?.cancel()
        currentTranslationJob = null
        isTranslating.value = false
        translatingFeedId = null
    }
}
