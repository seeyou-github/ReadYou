package me.ash.reader.infrastructure.translate.transtitle

import androidx.compose.runtime.mutableStateOf
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.translate.TranslateProvider
import me.ash.reader.infrastructure.translate.TranslateRuntimeConfigResolver
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateService
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateServiceFactory
import me.ash.reader.infrastructure.translate.cache.ArticleTranslationCacheService
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig
import timber.log.Timber

private const val TAG = "TitleTranslateEntry"
private const val TITLE_TRANSLATION_BATCH_SIZE = 50

private data class PendingTitleUpdate(
    val translatedTitle: String,
    val provider: String,
    val model: String,
)

@Singleton
class TitleTranslateEntry
@Inject
constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val articleDao: ArticleDao,
    private val feedDao: FeedDao,
    private val translationCacheService: ArticleTranslationCacheService,
    private val streamTranslateServiceFactory: StreamTranslateServiceFactory,
    private val settingsProvider: SettingsProvider,
    private val titleTranslateQueue: TitleTranslateQueue,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {
    val isTranslating = mutableStateOf(false)
    val translationProgress = mutableStateOf(0)
    val translationTotal = mutableStateOf(0)
    val translationError = MutableStateFlow<Throwable?>(null)
    private val _liveTranslatedTitles = MutableStateFlow<Map<String, String>>(emptyMap())
    val liveTranslatedTitles: StateFlow<Map<String, String>> = _liveTranslatedTitles.asStateFlow()

    private val debounceTime = 500L
    private var lastTriggerTime = 0L
    private var translatingFeedId: String? = null
    private var currentTranslationJob: Job? = null
    private var currentTranslateService: StreamTranslateService? = null
    private val pendingTitleUpdatesLock = Any()
    private val pendingTitleUpdates = mutableMapOf<String, PendingTitleUpdate>()

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
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    persistPendingTitleUpdatesSafely()
                }
                Timber.tag(TAG).d("title translation cancelled")
                throw e
            } catch (e: Exception) {
                persistPendingTitleUpdatesSafely()
                translationError.value = e
                Timber.tag(TAG).e(e, "title translation failed")
            } finally {
                isTranslating.value = false
                translatingFeedId = null
                currentTranslationJob = null
                currentTranslateService = null
            }
        }

    private suspend fun findArticlesNeedingTranslation(feedId: String, accountId: Int): List<Article> {
        val articles = articleDao.queryAllByFeedId(accountId, feedId)

        val cachedTranslatedTitles = mutableMapOf<String, String>()
        val articlesNeedingTranslation = mutableListOf<Article>()

        articles.forEach { article ->
            val cachedTitle = cachedTranslatedTitle(article)
            if (!cachedTitle.isNullOrBlank()) {
                cachedTranslatedTitles[article.id] = cachedTitle
            } else if (needsTranslation(article.title)) {
                articlesNeedingTranslation += article
            }
        }

        publishLiveTranslatedTitles(cachedTranslatedTitles)

        val batch = articlesNeedingTranslation.take(TITLE_TRANSLATION_BATCH_SIZE)

        Timber.tag(TAG).d(
            "title translation candidates: cached=${cachedTranslatedTitles.size}, " +
                "needApi=${articlesNeedingTranslation.size}, batch=${batch.size}"
        )

        return batch
    }

    private suspend fun cachedTranslatedTitle(article: Article): String? {
        article.translatedTitle?.takeIf { it.isNotBlank() }?.let { return it }
        liveTranslatedTitles.value[article.id]?.takeIf { it.isNotBlank() }?.let { return it }
        return translationCacheService.getCache(article.id)?.translatedTitle?.takeIf { it.isNotBlank() }
    }

    fun shouldRequestTitleTranslation(
        article: Article,
        liveTranslatedTitle: String? = liveTranslatedTitles.value[article.id],
    ): Boolean {
        if (!article.translatedTitle.isNullOrBlank()) return false
        if (!liveTranslatedTitle.isNullOrBlank()) return false
        return needsTranslation(article.title)
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

        val runtimeConfig = TranslateRuntimeConfigResolver.resolve(context, config)
        val translateService = streamTranslateServiceFactory.getService(runtimeConfig.provider)
        currentTranslateService = translateService
        val titleTranslateService = TitleTranslateService(translateService)

        titleTranslateService.translateTitles(
                titles = articles.map { it.title },
                articleIds = articles.map { it.id },
                config = runtimeConfig,
                translateService = translateService,
                onTitleTranslated = { articleId, translatedTitle ->
                    publishLiveTranslatedTitle(articleId, translatedTitle)
                    rememberPendingTitleUpdate(articleId, translatedTitle, runtimeConfig)
                },
                onProgress = { completed, _ ->
                    translationProgress.value = completed
                },
                onError = { error ->
                    translationError.value = error
                },
            )

        persistPendingTitleUpdates()
    }

    private fun publishLiveTranslatedTitle(articleId: String, translatedTitle: String) {
        publishLiveTranslatedTitles(mapOf(articleId to translatedTitle))
    }

    private fun publishLiveTranslatedTitles(translatedTitles: Map<String, String>) {
        if (translatedTitles.isEmpty()) return
        _liveTranslatedTitles.update { current ->
            current + translatedTitles
        }
    }

    private fun rememberPendingTitleUpdate(
        articleId: String,
        translatedTitle: String,
        config: TranslateModelConfig,
    ) {
        synchronized(pendingTitleUpdatesLock) {
            pendingTitleUpdates[articleId] =
                PendingTitleUpdate(
                    translatedTitle = translatedTitle,
                    provider = config.provider,
                    model = config.model,
                )
        }
    }

    private fun persistPendingTitleUpdatesAsync() {
        val hasPendingUpdates =
            synchronized(pendingTitleUpdatesLock) {
                pendingTitleUpdates.isNotEmpty()
            }
        if (!hasPendingUpdates) return

        applicationScope.launch(ioDispatcher) {
            persistPendingTitleUpdatesSafely()
        }
    }

    private suspend fun persistPendingTitleUpdatesSafely() {
        try {
            persistPendingTitleUpdates()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "failed to persist completed title translations")
        }
    }

    private suspend fun persistPendingTitleUpdates() {
        val updates =
            synchronized(pendingTitleUpdatesLock) {
                pendingTitleUpdates.toMap()
            }
        if (updates.isEmpty()) return

        articleDao.batchUpdateTranslatedTitle(
            updates.mapValues { (_, update) -> update.translatedTitle }
        )

        updates.forEach { (articleId, update) ->
            updateTitleCache(
                articleId = articleId,
                translatedTitle = update.translatedTitle,
                provider = update.provider,
                model = update.model,
            )
        }

        synchronized(pendingTitleUpdatesLock) {
            updates.forEach { (articleId, update) ->
                if (pendingTitleUpdates[articleId] == update) {
                    pendingTitleUpdates.remove(articleId)
                }
            }
        }
    }

    private suspend fun updateTitleCache(
        articleId: String,
        translatedTitle: String,
        provider: String,
        model: String,
    ) {
        translationCacheService.updateTitleOnly(
            articleId = articleId,
            translatedTitle = translatedTitle,
            provider = provider,
            model = model,
        )
    }

    fun cancelTranslation(feedId: String) {
        if (translatingFeedId != feedId) return
        currentTranslateService?.cancel()
        currentTranslationJob?.cancel()
        currentTranslationJob = null
        isTranslating.value = false
        persistPendingTitleUpdatesAsync()
    }

    fun cancelAllTranslations() {
        currentTranslateService?.cancel()
        currentTranslationJob?.cancel()
        currentTranslationJob = null
        isTranslating.value = false
        translatingFeedId = null
        persistPendingTitleUpdatesAsync()
    }
}
