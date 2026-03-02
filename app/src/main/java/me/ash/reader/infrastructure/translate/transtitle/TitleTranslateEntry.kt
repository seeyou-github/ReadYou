package me.ash.reader.infrastructure.translate.transtitle

import androidx.compose.runtime.mutableStateOf
import java.util.Calendar
import java.util.Date
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.translate.TranslateProvider
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateServiceFactory
import me.ash.reader.infrastructure.translate.cache.ArticleTranslationCacheService
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import timber.log.Timber

private const val TAG = "TitleTranslateEntry"

@Singleton
class TitleTranslateEntry
@Inject
constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val translationCacheService: ArticleTranslationCacheService,
    private val streamTranslateServiceFactory: StreamTranslateServiceFactory,
    private val settingsProvider: SettingsProvider,
    private val titleTranslateQueue: TitleTranslateQueue,
) {
    val isTranslating = mutableStateOf(false)
    val translationProgress = mutableStateOf(0)
    val translationTotal = mutableStateOf(0)
    val translationError = MutableStateFlow<Throwable?>(null)

    private val debounceTime = 500L
    private var lastTriggerTime = 0L
    private var translatingFeedId: String? = null
    private var currentTranslationJob: Job? = null

    suspend fun triggerTranslation(feedId: String, triggerSource: String = "unknown") =
        withContext(ioDispatcher) {
            if (translatingFeedId == feedId) {
                Timber.tag(TAG).d("skip, feed is already translating: $feedId")
                return@withContext
            }

            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < debounceTime && translatingFeedId != null) {
                Timber.tag(TAG).d("skip by debounce: source=$triggerSource")
                return@withContext
            }
            lastTriggerTime = now

            val feed = feedDao.queryById(feedId)
            if (feed == null || !feed.isAutoTranslateTitle) {
                Timber.tag(TAG).d("skip, auto title translation is disabled: $feedId")
                return@withContext
            }

            val articlesToTranslate = findArticlesNeedingTranslation(feedId, feed.accountId)
            if (articlesToTranslate.isEmpty()) {
                Timber.tag(TAG).d("no articles need title translation: $feedId")
                return@withContext
            }

            val enqueued = titleTranslateQueue.enqueueTask(feedId, feed.name, triggerSource)
            if (!enqueued) {
                Timber.tag(TAG).d("skip, task already queued or processing: $feedId")
                return@withContext
            }

            translationTotal.value = articlesToTranslate.size
            translationProgress.value = 0
            isTranslating.value = true
            translatingFeedId = feedId

            try {
                performTranslation(articlesToTranslate, feed)
            } catch (e: Exception) {
                translationError.value = e
                Timber.tag(TAG).e(e, "title translation failed")
            } finally {
                isTranslating.value = false
                translatingFeedId = null
            }
        }

    private suspend fun findArticlesNeedingTranslation(feedId: String, accountId: Int): List<Article> {
        val (todayStart, todayEndExclusive) = dayBounds(Date())
        var articles =
            articleDao.queryByFeedIdUpdatedBetween(
                accountId = accountId,
                feedId = feedId,
                start = todayStart,
                endExclusive = todayEndExclusive,
            )

        if (articles.isEmpty()) {
            val latestUpdateAt = articleDao.queryLatestUpdateAtByFeedId(accountId, feedId)
            if (latestUpdateAt != null) {
                val (latestStart, latestEndExclusive) = dayBounds(latestUpdateAt)
                articles =
                    articleDao.queryByFeedIdUpdatedBetween(
                        accountId = accountId,
                        feedId = feedId,
                        start = latestStart,
                        endExclusive = latestEndExclusive,
                    )
            }
        }

        return articles.filter { article ->
            article.translatedTitle.isNullOrBlank() && needsTranslation(article.title)
        }
    }

    private fun dayBounds(baseDate: Date): Pair<Date, Date> {
        val calendar =
            Calendar.getInstance().apply {
                time = baseDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val start = calendar.time
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        return start to calendar.time
    }

    private fun needsTranslation(title: String): Boolean {
        val hasChinese = Pattern.compile("[\\u4e00-\\u9fa5]").matcher(title).find()
        if (hasChinese) return false

        val hasEnglish = Pattern.compile("[a-zA-Z]").matcher(title).find()
        if (!hasEnglish) return false

        return true
    }

    private suspend fun performTranslation(articles: List<Article>, feed: Feed) = kotlinx.coroutines.coroutineScope {
        currentTranslationJob = coroutineContext[Job]
        ensureActive()

        val config =
            settingsProvider.settings.quickTranslateModel
                ?: TranslateModelConfig(
                    provider = TranslateProvider.SILICONFLOW.serviceId,
                    model = "",
                    apiKey = "",
                )

        val translateService = streamTranslateServiceFactory.getService(config.provider)
        val titleTranslateService = TitleTranslateService(translateService)

        val results =
            titleTranslateService.translateTitles(
                titles = articles.map { it.title },
                articleIds = articles.map { it.id },
                config = config,
                translateService = translateService,
                onProgress = { completed, _ ->
                    translationProgress.value = completed
                },
                onError = { error ->
                    translationError.value = error
                },
            )

        results.forEach { (articleId, translatedTitle) ->
            updateCacheAndDatabase(articleId, translatedTitle, config)
        }
    }

    private suspend fun updateCacheAndDatabase(
        articleId: String,
        translatedTitle: String,
        config: TranslateModelConfig,
    ) {
        articleDao.updateTranslatedTitle(articleId, translatedTitle)
        translationCacheService.updateTitleOnly(
            articleId = articleId,
            translatedTitle = translatedTitle,
            provider = config.provider,
            model = config.model,
        )
    }

    fun cancelTranslation(feedId: String) {
        if (translatingFeedId != feedId) return
        currentTranslationJob?.cancel()
        currentTranslationJob = null
        isTranslating.value = false
    }

    fun cancelAllTranslations() {
        currentTranslationJob?.cancel()
        currentTranslationJob = null
        isTranslating.value = false
        translatingFeedId = null
    }
}
