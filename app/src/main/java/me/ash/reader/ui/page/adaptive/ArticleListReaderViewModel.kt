package me.ash.reader.ui.page.adaptive

import android.net.Uri
import android.util.Log
import androidx.compose.ui.util.fastFirstOrNull
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import kotlin.collections.any
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.domain.data.ArticlePagingListUseCase
import me.ash.reader.domain.data.DiffMapHolder
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.data.PagerData
import me.ash.reader.domain.model.article.Article
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.ArticleWithFeed
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.MarkAsReadConditions
import me.ash.reader.domain.service.GoogleReaderRssService
import me.ash.reader.domain.service.LocalRssService
import me.ash.reader.domain.service.RssService
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.infrastructure.android.AndroidImageDownloader
import me.ash.reader.infrastructure.android.TextToSpeechManager
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.PullToLoadNextFeedPreference
import me.ash.reader.infrastructure.preference.SettingsProvider
import me.ash.reader.infrastructure.rss.ReaderCacheHelper

import me.ash.reader.infrastructure.translate.cache.ArticleTranslationCacheService

import me.ash.reader.infrastructure.translate.apistream.StreamTranslateManager
import me.ash.reader.infrastructure.translate.apistream.StreamTranslateServiceFactory
import  me.ash.reader.infrastructure.translate.ui.TranslateState
import me.ash.reader.infrastructure.translate.preference.TranslateServiceIdPreference
import me.ash.reader.infrastructure.translate.transtitle.TitleTranslateEntry
import me.ash.reader.infrastructure.translate.transtitle.TitleTranslateQueue
import me.ash.reader.plugin.PluginConstants
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.plugin.PluginSyncService
import org.jsoup.Jsoup
import timber.log.Timber

private const val TAG = "ArticleListReaderViewModel"

@OptIn(FlowPreview::class)
@HiltViewModel()
class ArticleListReaderViewModel
@Inject constructor(
    private val rssService: RssService,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
    val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
    private val settingsProvider: SettingsProvider,
    private val readerCacheHelper: ReaderCacheHelper,
    val textToSpeechManager: TextToSpeechManager,
    private val imageDownloader: AndroidImageDownloader,
    private val articleListUseCase: ArticlePagingListUseCase,
    workManager: WorkManager,


    val streamTranslateServiceFactory: StreamTranslateServiceFactory,
    private val translationCacheService: ArticleTranslationCacheService,
    private val articleDao: ArticleDao,
    val titleTranslateEntry: TitleTranslateEntry,
    val titleTranslateQueue: TitleTranslateQueue,
    private val pluginRuleDao: PluginRuleDao,
    private val pluginSyncService: PluginSyncService,
) : ViewModel() {

    val flowUiState: StateFlow<FlowUiState?> =
        articleListUseCase.pagerFlow.combine(groupWithFeedsListUseCase.groupWithFeedListFlow) { pagerData, groupWithFeedsList ->
                val filterState = pagerData.filterState
                var nextFilterState: FilterState? = null
                if (filterState.group != null) {
                    val groupList = groupWithFeedsList.map { it.group }
                    val index = groupList.indexOfFirst { it.id == filterState.group.id }
                    if (index != -1) {
                        val nextGroup = groupList.getOrNull(index + 1)
                        if (nextGroup != null) {
                            nextFilterState = filterState.copy(group = nextGroup)
                        }
                    } else {
                        val allGroupList =
                            rssService.get().queryAllGroupWithFeeds().map { it.group }
                        val index = allGroupList.indexOfFirst { it.id == filterState.group.id }
                        if (index != -1) {
                            val nextGroup =
                                allGroupList.subList(index, allGroupList.size).fastFirstOrNull {
                                    groupList.map { it.id }.contains(it.id)
                                }
                            if (nextGroup != null) {
                                nextFilterState = filterState.copy(group = nextGroup)
                            }
                        }
                    }
                } else if (filterState.feed != null) {
                    val feedList = groupWithFeedsList.flatMap { it.feeds }
                    val index = feedList.indexOfFirst { it.id == filterState.feed.id }
                    if (index != -1) {
                        val nextFeed = feedList.getOrNull(index + 1)
                        if (nextFeed != null) {
                            nextFilterState = filterState.copy(feed = nextFeed)
                        }
                    } else {
                        val allFeedList =
                            rssService.get().queryAllGroupWithFeeds().flatMap { it.feeds }
                        val index = allFeedList.indexOfFirst { it.id == filterState.feed.id }
                        if (index != -1) {
                            val nextFeed =
                                allFeedList.subList(index, allFeedList.size).fastFirstOrNull {
                                    feedList.map { it.id }.contains(it.id)
                                }
                            if (nextFeed != null) {
                                nextFilterState = filterState.copy(feed = nextFeed)
                            }
                        }
                    }
                }
                FlowUiState(nextFilterState = nextFilterState, pagerData = pagerData)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val syncWorkerStatusFlow = workManager.getWorkInfosByTagFlow(SyncWorker.SYNC_TAG)
        .map { it.any { workInfo -> workInfo.state == WorkInfo.State.RUNNING } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow = _isSyncingFlow.asStateFlow()

    init {
        viewModelScope.launch {
            syncWorkerStatusFlow.debounce(500L).collect { _isSyncingFlow.value = it }
        }

        // 监听文章数量变化，触发自动翻译
        viewModelScope.launch {
            articleListUseCase.itemSnapshotListFlow
                .collect { itemSnapshotList ->
                    val currentCount = itemSnapshotList.filterIsInstance<ArticleFlowItem.Article>().size
                    val currentFeed = filterStateUseCase.filterStateFlow.value.feed

                    Timber.tag("AutoTranslateTitle").d("文章数量变化：${previousArticleCount} -> $currentCount")

                    // 检查是否有新文章
                    if (currentCount > previousArticleCount && currentFeed?.isAutoTranslateTitle == true) {
                        Timber.tag("AutoTranslateTitle").d("检测到新文章，触发标题翻译")
                        // 直接调用 TitleTranslateEntry，传入触发来源
                        titleTranslateEntry.triggerTranslation(currentFeed.id, "article_count_change")
                    }

                    previousArticleCount = currentCount
                }
        }
    }

    fun updateReadStatus(
        groupId: String?,
        feedId: String?,
        articleId: String?,
        conditions: MarkAsReadConditions,
        isUnread: Boolean,
    ) {
        applicationScope.launch(ioDispatcher) {
            rssService.get().markAsRead(
                    groupId = groupId,
                    feedId = feedId,
                    articleId = articleId,
                    before = conditions.toDate(),
                    isUnread = isUnread,
                )
        }
    }

    fun updateStarredStatus(articleId: String?, isStarred: Boolean) {
        applicationScope.launch(ioDispatcher) {
            if (articleId != null) {
                rssService.get().markAsStarred(articleId = articleId, isStarred = isStarred)
            }
        }
    }

    fun markAsReadFromListByDate(date: Date, isBefore: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val items =
                articleListUseCase.itemSnapshotList.filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }.filter {
                        if (isBefore) {
                            date > it.article.date && it.article.isUnread
                        } else {
                            date < it.article.date && it.article.isUnread
                        }
                    }.distinctBy { it.article.id }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun loadNextFeedOrGroup() {
        viewModelScope.launch {
            if (settingsProvider.settings.pullToSwitchFeed == PullToLoadNextFeedPreference.MarkAsReadAndLoadNextFeed) {
                markAllAsRead()
            }
            flowUiState.value?.nextFilterState?.let { filterStateUseCase.updateFilterState(it) }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val items =
                articleListUseCase.itemSnapshotList.items.filterIsInstance<ArticleFlowItem.Article>()
                    .map { it.articleWithFeed }

            diffMapHolder.updateDiff(articleWithFeed = items.toTypedArray(), isUnread = false)
        }
    }

    fun sync() {
        diffMapHolder.commitDiffsToDb()
        viewModelScope.launch {
            _isSyncingFlow.value = true
            val isSyncing = syncWorkerStatusFlow.value
            if (!isSyncing) {
                delay(1000L)
                if (syncWorkerStatusFlow.value == false) {
                    _isSyncingFlow.value = false
                }
            }
        }
        applicationScope.launch(ioDispatcher) {
            val filterState = filterStateUseCase.filterStateFlow.value
            val service = rssService.get()
            when (service) {
                is LocalRssService -> service.doSyncOneTime(
                    feedId = filterState.feed?.id,
                    groupId = filterState.group?.id,
                )

                is GoogleReaderRssService -> service.doSyncOneTime(
                    feedId = filterState.feed?.id,
                    groupId = filterState.group?.id,
                )

                else -> service.doSyncOneTime()
            }
        }
    }

    fun cancelSync() {
        rssService.get().cancelSync()
    }

    fun resetFilter() =
        filterStateUseCase.updateFilterState(feed = null, group = null, searchContent = null)

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(
            filterState.feed,
            filterState.group,
            filterState.filter,
        )
    }

    fun inputSearchContent(content: String? = null) {
        if (content != filterStateUseCase.filterStateFlow.value.searchContent) filterStateUseCase.updateFilterState(
            searchContent = content
        )
    }

    private val _readingUiState = MutableStateFlow(ReadingUiState())
    val readingUiState: StateFlow<ReadingUiState> = _readingUiState.asStateFlow()

    private val _readerState: MutableStateFlow<ReaderState> = MutableStateFlow(ReaderState())
    val readerStateStateFlow = _readerState.asStateFlow()

    private val _translationState: MutableStateFlow<TranslationState> = MutableStateFlow(TranslationState())
    val translationStateStateFlow: StateFlow<TranslationState> = _translationState.asStateFlow()

    // 标题翻译状态
    private var previousArticleCount = 0

    // 翻译任务的 Job，用于取消翻译
    private var translationJob: kotlinx.coroutines.Job? = null

    // TranslateManager 引用，用于停止翻译
    private var streamTranslateManager: StreamTranslateManager? = null

    private val currentArticle: Article?
        get() = readingUiState.value.articleWithFeed?.article

    private val currentFeed: Feed?
        get() = readingUiState.value.articleWithFeed?.feed

    fun initData(articleId: String, listIndex: Int? = null) {
        // 取消正在进行的翻译任务
        cancelTranslation()

        viewModelScope.launch {
            val snapshotList = articleListUseCase.itemSnapshotList

            val itemByIndex =
                listIndex?.let { snapshotList.getOrNull(it) as? ArticleFlowItem.Article }

            val itemFromList =
                if (itemByIndex != null && itemByIndex.articleWithFeed.article.id != articleId) {
                    itemByIndex
                } else {
                    snapshotList.find { item ->
                        item is ArticleFlowItem.Article && item.articleWithFeed.article.id == articleId
                    } as? ArticleFlowItem.Article
                }

            val item =
                itemByIndex?.articleWithFeed ?: (itemFromList?.articleWithFeed ?: rssService.get()
                    .findArticleById(articleId)!!)

            if (diffMapHolder.checkIfUnread(item)) {
                diffMapHolder.updateDiff(item, isUnread = false)
            }
            item.run {
                _readingUiState.update {
                    it.copy(articleWithFeed = this, isStarred = article.isStarred, isUnread = false)
                }
                // 清理旧的 StreamTranslateManager（如果存在）
                streamTranslateManager?.destroy()
                streamTranslateManager = null
                _readerState.update {
                    it.copy(
                        articleId = article.id,
                        feedName = feed.name,
                        title = article.title,
                        author = article.author,
                        link = article.link,
                        publishedDate = article.date,
                    ).prefetchArticleId().renderContent(this)
                }

                // 重置翻译状态
                _translationState.update {
                    TranslationState()
                }
            }

            // 检查并加载翻译缓存
//            checkAndLoadCachedTranslation(articleId)
        }
    }



    fun clearReadingData() {
        _readingUiState.update { ReadingUiState() }
        _readerState.update { ReaderState() }
    }

    suspend fun ReaderState.renderContent(articleWithFeed: ArticleWithFeed): ReaderState {
        val contentState = if (articleWithFeed.feed.url.startsWith(PluginConstants.PLUGIN_URL_PREFIX)) {
            val content = ensurePluginContent(articleWithFeed)
            if (content.isNullOrBlank()) {
                ReaderState.Description("")
            } else {
                val filtered =
                    readerCacheHelper.removeFilteredImagesIfNeeded(
                        content,
                        articleWithFeed.feed,
                    )
                ReaderState.Description(filtered)
            }
        } else if (articleWithFeed.feed.isFullContent) {
            val fullContent =
                readerCacheHelper.readFullContent(articleWithFeed.article.id).getOrNull()
            if (fullContent != null) {
                val filtered =
                    readerCacheHelper.removeFilteredImagesIfNeeded(fullContent, articleWithFeed.feed)
                ReaderState.FullContent(filtered)
            }
            else {
                renderFullContent()
                ReaderState.Loading
            }
        } else {
            val filtered =
                readerCacheHelper.removeFilteredImagesIfNeeded(
                    articleWithFeed.article.rawDescription,
                    articleWithFeed.feed,
                )
            ReaderState.Description(filtered)
        }

        val contentForLog =
            when (contentState) {
                is ReaderState.Description -> contentState.content
                is ReaderState.FullContent -> contentState.content
                else -> null
            }
        if (!contentForLog.isNullOrBlank()) {
            logContentImages(contentForLog)
        }

        return copy(content = contentState)
    }

    private fun logContentImages(content: String) {
        val images =
            runCatching { Jsoup.parse(content).select("img").mapNotNull { it.attr("src") } }
                .getOrDefault(emptyList())
        if (images.isNotEmpty()) {
            Log.d("RLog", "content images count=${images.size}, samples=${images.take(5)}")
        } else {
            Log.d("RLog", "content images count=0")
        }
    }

    private suspend fun ensurePluginContent(articleWithFeed: ArticleWithFeed): String? {
        val article = articleWithFeed.article
        if (article.rawDescription.isNotBlank()) return article.rawDescription
        val feed = articleWithFeed.feed
        val ruleId = feed.url.removePrefix(PluginConstants.PLUGIN_URL_PREFIX)
        val rule = pluginRuleDao.queryById(ruleId) ?: return null
        val detail = pluginSyncService.fetchDetail(rule, article.link).getOrNull() ?: return null
        val content = detail.contentHtml
        if (content.isBlank()) return null
        val plainText = Jsoup.parse(content).text()
        val updated =
            article.copy(
                title = detail.title ?: article.title,
                author = detail.author ?: article.author,
                rawDescription = content,
                shortDescription = plainText.take(280),
                img = detail.coverImage ?: article.img,
                sourceTime = detail.time ?: article.sourceTime,
            )
        articleDao.update(updated)
        _readingUiState.update { it.copy(articleWithFeed = articleWithFeed.copy(article = updated)) }
        return content
    }

    fun renderDescriptionContent() {
        _readerState.update {
            val filtered =
                readerCacheHelper.removeFilteredImagesIfNeeded(
                    currentArticle?.rawDescription ?: "",
                    currentFeed ?: return@update it,
                )
            it.copy(
                content = ReaderState.Description(content = filtered)
            )
        }
    }

    fun renderFullContent() {
        val fetchJob = viewModelScope.launch {
            readerCacheHelper.readOrFetchFullContent(currentArticle!!).onSuccess { content ->
                    val filtered =
                        readerCacheHelper.removeFilteredImagesIfNeeded(
                            content,
                            currentFeed ?: return@onSuccess,
                        )
                    _readerState.update {
                        it.copy(content = ReaderState.FullContent(content = filtered))
                    }
                }.onFailure { th ->
                    _readerState.update {
                        it.copy(content = ReaderState.Error(th.message.toString()))
                    }
                }
        }
        viewModelScope.launch {
            delay(100L)
            if (fetchJob.isActive) {
                setLoading()
            }
        }
    }




    /**
     * 开始翻译（由 ReadingPage 调用）
     * 修改日期：2026-01-31
     * 修改原因：改用 StreamTranslateManager 实现 SSE 流式翻译
     * @param manager StreamTranslateManager 实例
     */
    fun startTranslation(manager: StreamTranslateManager) {
        Timber.d("[$TAG] ========== ArticleListReaderViewModel.startTranslation ==========")
        val currentState = _readerState.value
        val title = currentState.title
        val articleId = currentState.articleId

        Timber.d("[$TAG] 参数: title=${title?.take(50) ?: "null"}")
        val currentTranslationState = _translationState.value
        Timber.d("[$TAG] 当前状态: translateState=${currentTranslationState.translateState}")

        // 保存 StreamTranslateManager 引用
        streamTranslateManager = manager

        // 设置回调
        manager.onStateChanged = { state ->
            Timber.d("[$TAG] 状态变化: $state")
            _translationState.update { it.copy(translateState = state) }
        }

        manager.onProgress = { current, total ->
            Timber.d("[$TAG] 进度更新: $current / $total")
        }

        manager.onError = { error ->
            Timber.e("[$TAG] 翻译错误: $error")
            _translationState.update {
                it.copy(
                    translateState = TranslateState.Idle,
                    translateError = error
                )
            }
        }

        manager.onComplete = { fullHtml ->
            Timber.d("[$TAG] 翻译完成，HTML长度: ${fullHtml.length}")
            
            // 更新状态为已翻译
            _translationState.update { it.copy(translateState = TranslateState.Translated) }
            
            // 保存到缓存
            if (articleId != null) {
                val feedId = currentArticle?.feedId
                if (feedId != null) {
                    val config = manager.getConfig()
                    viewModelScope.launch {
                        translationCacheService.saveCache(
                            articleId = articleId,
                            feedId = feedId,
                            translatedTitle = _translationState.value.translatedTitle,
                            fullHtmlContent = fullHtml,
                            provider = config.provider,
                            model = config.model,
                        )
                        Timber.d("[$TAG] 翻译缓存已保存")

                        // 更新Article的翻译状态
                        articleDao.updateTranslationStatus(articleId, true)
                    }
                }
            }
        }

        manager.onTitleTranslated = { translatedTitle ->
            Timber.d("[$TAG] 标题翻译完成: $translatedTitle")
            _translationState.update { it.copy(translatedTitle = translatedTitle) }
        }

        // 修改日期：2026-01-31
        // 检查DOM翻译标志位，避免重复翻译
        viewModelScope.launch {
            val domHasTranslation = manager.checkHasTranslation()
            if (domHasTranslation) {
                Timber.d("[$TAG] DOM已包含翻译，直接显示译文")
                // 直接显示译文（不进行翻译流程）
                manager.toggleTranslationDisplay(true)
                _translationState.update {
                    it.copy(
                        translateState = TranslateState.Translated,
                    )
                }
                return@launch
            }

            // DOM未包含翻译，继续检查缓存或开始新翻译
            checkCacheAndStartNewTranslation(manager, title, currentState, articleId)
        }
    }

    /**
     * 检查缓存并开始新翻译（内部方法）
     *
     * 修改日期：2026-01-31
     * 修改原因：改用 StreamTranslateManager 实现 SSE 流式翻译
     */
    private suspend fun checkCacheAndStartNewTranslation(
        manager: StreamTranslateManager,
        title: String?,
        currentState: ReaderState,
        articleId: String?
    ) {
        Timber.d("[$TAG]  检查缓存并开始新翻译（内部方法）：有缓存就加载缓存，然后切换译文，没有则开始翻译流程")
        // 检查是否有缓存
        if (articleId != null) {
            val cache = translationCacheService.getCache(articleId)
            if (cache != null) {
                Timber.d("[$TAG] 找到翻译缓存，直接加载")
                // 有缓存，直接加载
                // 默认显示原文，用户点击按钮后切换到译文
                _translationState.update {
                    it.copy(
                        translateState = TranslateState.Translated,
                        originalTitle = title,
                        translatedTitle = cache.translatedTitle,
                        originalContent = currentState.content,
                        translatedContent = ReaderState.FullContent(cache.fullHtmlContent ?: ""),
                    )
                }
                //这边切换了译文，翻译状态需要同步
                _readerState.update {
                    it.copy(
                        title = title,  // 默认显示原文标题
                        content = currentState.content  // 默认显示原文内容，避免"显示全文"按钮误判
                    )
                }

                // 设置翻译标志位
                manager.restoreFromCache()
                
                // 切换缓存中译文
                manager.toggleTranslationDisplay(true)
                _translationState.update {
                    it.copy(
                        translateState = TranslateState.Translated,
                    )
                }
                // 更新 Article 的翻译状态
                articleDao.updateTranslationStatus(articleId, true)
                
                Timber.d("[$TAG] 翻译缓存已加载")
                return
            }

            // 没有缓存，开始新的翻译流程
            startNewTranslation(manager, title, currentState)
        } else {
            // 没有文章ID，开始新的翻译流程
            startNewTranslation(manager, title, currentState)
        }
    }

    /**
     * 开始新的翻译流程（内部方法）
     * 修改日期：2026-01-31
     * 修改原因：改用 StreamTranslateManager 实现 SSE 流式翻译
     */
    private fun startNewTranslation(
        manager: StreamTranslateManager,
        title: String?,
        currentState: ReaderState
    ) {
        // 保存原文（如果还没有保存过）
        val currentTranslationState = _translationState.value
        val shouldSaveOriginal = currentTranslationState.originalTitle == null || currentTranslationState.originalContent == null
        _translationState.update {
            it.copy(
                translateState = TranslateState.Translating,
                originalTitle = if (shouldSaveOriginal) title else it.originalTitle,
                originalContent = if (shouldSaveOriginal) currentState.content else it.originalContent
            )
        }
        if (shouldSaveOriginal) {
            Timber.d("[$TAG] 已保存原文")
        } else {
            Timber.d("[$TAG] 原文已存在，无需重复保存")
        }

        // 开始SSE流式翻译（不需要config，因为StreamTranslateManager在构造时已经有了initialConfig）
        Timber.d("[$TAG] 调用 manager.startStreamTranslation")
        manager.startStreamTranslation(title)
    }

    /**
     * 取消翻译任务
     */
    fun cancelTranslation() {
        Timber.d("[$TAG] ==========初次进入页面，需要重置状态，加载的翻译缓存dom被清除，翻译是再次加载 ==========")
        Timber.d("[$TAG] ========== ArticleListReaderViewModel.cancelTranslation ==========")

        // 清除所有翻译并停止翻译
        viewModelScope.launch {
            streamTranslateManager?.clearTranslations()
            streamTranslateManager?.cancel()
            streamTranslateManager = null
        }

        // 更新状态
        _translationState.update {
            it.copy(
                translateState = TranslateState.Idle,
                translationProgress = 0,
                totalSegments = 0,
            )
        }
        _readerState.update {
            it.copy(
                title = _translationState.value.originalTitle ?: it.title,
                content = _translationState.value.originalContent ?: it.content
            )
        }

        Timber.d("[$TAG] 翻译已取消")
    }

    /**
     * 获取保存为 HTML 的文章内容（优先使用全文内容）
     */
    suspend fun getArticleContentForSave(articleWithFeed: ArticleWithFeed): String =
        kotlinx.coroutines.withContext(ioDispatcher) {
            val article = articleWithFeed.article
            if (articleWithFeed.feed.isFullContent) {
                readerCacheHelper.readOrFetchFullContent(article).getOrNull()
                    ?: article.rawDescription
            } else {
                article.rawDescription
            }
        }

    /**
     * 切换当前 Feed 的自动翻译标题设置
     *
     * 修改日期：2026-02-02
     * 修改原因：在 FlowPage 顶栏添加自动翻译标题按钮
     * 修改日期：2026-02-03
     * 修改原因：添加取消翻译任务逻辑
     */
    fun toggleTitleTranslate() {
        viewModelScope.launch(ioDispatcher) {
            val currentFilterState = filterStateUseCase.filterStateFlow.value
            val currentFeed = currentFilterState.feed

            if (currentFeed == null) {
                Timber.tag("AutoTranslateTitle").e("toggleTitleTranslate: 当前没有选中 Feed，无法切换自动翻译标题设置")
                return@launch
            }

            val currentValue = currentFeed.isAutoTranslateTitle
            val newValue = !currentValue

            Timber.tag("AutoTranslateTitle").d("toggleTitleTranslate: Feed ID = ${currentFeed.id}, Feed 名称 = ${currentFeed.name}, 当前值 = $currentValue, 新值 = $newValue")

            // 如果切换为 false，取消该 Feed 的翻译任务
            if (!newValue && currentValue) {
                Timber.tag("AutoTranslateTitle").d("toggleTitleTranslate: 取消 Feed ${currentFeed.id} 的翻译任务")
                titleTranslateQueue.cancelTaskByFeedId(currentFeed.id)
                titleTranslateEntry.cancelTranslation(currentFeed.id)
            }

            // 更新 Feed 的 isAutoTranslateTitle 设置
            val updatedFeed = currentFeed.copy(isAutoTranslateTitle = newValue)
            rssService.get().updateFeed(updatedFeed)

            Timber.tag("AutoTranslateTitle").d("toggleTitleTranslate: 数据库更新完成，Feed ${currentFeed.id} 的 isAutoTranslateTitle 已设置为 $newValue")

            // 刷新当前 FilterState 以更新 UI（会刷新列表，但是无所谓了）
            filterStateUseCase.updateFilterState(
                feed = updatedFeed,
                group = currentFilterState.group,
                filter = currentFilterState.filter,
            )

            Timber.tag("AutoTranslateTitle").d("toggleTitleTranslate: FilterState 已刷新，UI 将显示新状态 $newValue")
        }
    }

    /**
     * 触发标题翻译
     *
     * 修改日期：2026-02-03
     * 修改原因：新增标题翻译功能，为当前 Feed 下的文章翻译标题
     */
    fun triggerTitleTranslation() {
        viewModelScope.launch(ioDispatcher) {
            val currentFilterState = filterStateUseCase.filterStateFlow.value
            val currentFeed = currentFilterState.feed

            if (currentFeed == null) {
                Timber.tag("AutoTranslateTitle").w("triggerTitleTranslation: 当前没有选中 Feed，无法触发标题翻译")
                return@launch
            }

            if (!currentFeed.isAutoTranslateTitle) {
                Timber.tag("AutoTranslateTitle").d("triggerTitleTranslation: Feed ${currentFeed.id} 未开启自动翻译标题，跳过翻译")
                return@launch
            }

            Timber.tag("AutoTranslateTitle").d("triggerTitleTranslation: 开始为 Feed ${currentFeed.id} 翻译标题")

            // 调用 TitleTranslateEntry.triggerTranslation()，传入触发来源
            titleTranslateEntry.triggerTranslation(currentFeed.id, "manual")

            Timber.tag("AutoTranslateTitle").d("triggerTitleTranslation: 标题翻译完成")
        }
    }

    /**
     * ???????????????
     *
     * ?????2026-02-03
     */
    fun triggerTitleTranslationForGroup(groupId: String) {
        viewModelScope.launch(ioDispatcher) {
            val groups = groupWithFeedsListUseCase.groupWithFeedListFlow.value
            val groupWithFeed = groups.firstOrNull { it.group.id == groupId }

            if (groupWithFeed == null) {
                Timber.tag("AutoTranslateTitle").w(
                    "triggerTitleTranslationForGroup: ??????groupId=$groupId"
                )
                return@launch
            }

            val feedsToTranslate = groupWithFeed.feeds.filter { it.isAutoTranslateTitle }
            Timber.tag("AutoTranslateTitle").d(
                "triggerTitleTranslationForGroup: groupId=$groupId, groupName=${groupWithFeed.group.name}, feeds=${groupWithFeed.feeds.size}, autoFeeds=${feedsToTranslate.size}"
            )

            feedsToTranslate.forEach { feed ->
                Timber.tag("AutoTranslateTitle").d(
                    "triggerTitleTranslationForGroup: ????????? -> feedId=${feed.id}, feedName=${feed.name}"
                )
                titleTranslateEntry.triggerTranslation(feed.id, "group_flow")
            }
        }
    }


    /**
     * 显示原文（已翻译状态下点击按钮）
     */
    fun showOriginal() {
        val currentState = _readerState.value
        val currentTranslationState = _translationState.value

        viewModelScope.launch {
            // 切换 DOM 显示原文
            streamTranslateManager?.toggleTranslationDisplay(false)

            // 更新状态
            _translationState.update {
                it.copy(
                    translateState = TranslateState.Idle,
                    translationProgress = 0
                )
            }
            _readerState.update {
                it.copy(
                    title = currentTranslationState.originalTitle ?: it.title,
                    content = currentTranslationState.originalContent ?: it.content,
                )
            }

            Timber.d("[$TAG] 显示原文")
        }
    }

    /**
     * 关闭翻译错误对话框
     */
    fun dismissTranslateError() {
        _translationState.update { it.copy(translateError = null) }
    }

    /**
     * 当前翻译服务ID (响应式)
     */
    val currentTranslateServiceId = settingsProvider.dataStore
        .map { preferences ->
            TranslateServiceIdPreference.fromPreferences(preferences)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = TranslateServiceIdPreference.default
        )

    



    private fun setLoading() {
        _readerState.update { it.copy(content = ReaderState.Loading) }
    }

    fun ReaderState.prefetchArticleId(): ReaderState {
        val items = articleListUseCase.itemSnapshotList
        val currentId = currentArticle?.id
        val index = items.indexOfFirst { item ->
            item is ArticleFlowItem.Article && item.articleWithFeed.article.id == currentId
        }
        var previousArticle: ReaderState.PrefetchResult? = null
        var nextArticle: ReaderState.PrefetchResult? = null

        if (index != -1 || currentId == null) {
            val prevIterator = items.listIterator(index)
            while (prevIterator.hasPrevious()) {
                val previousIndex = prevIterator.previousIndex()
                val prev = prevIterator.previous()
                if (prev is ArticleFlowItem.Article) {
                    previousArticle = ReaderState.PrefetchResult(
                        articleId = prev.articleWithFeed.article.id,
                        index = previousIndex,
                    )
                    break
                }
            }
            val nextIterator = items.listIterator(index + 1)
            while (nextIterator.hasNext()) {
                val nextIndex = nextIterator.nextIndex()
                val next = nextIterator.next()
                if (next is ArticleFlowItem.Article && next.articleWithFeed.article.id != currentId) {
                    nextArticle = ReaderState.PrefetchResult(
                        articleId = next.articleWithFeed.article.id,
                        index = nextIndex,
                    )
                    break
                }
            }
        }

        Timber.d("$previousArticle, $nextArticle, $listIndex")
        return copy(nextArticle = nextArticle, previousArticle = previousArticle, listIndex = index)
    }

    fun downloadImage(
        url: String,
        onSuccess: (Uri) -> Unit = {},
        onFailure: (Throwable) -> Unit = {},
    ) {
        viewModelScope.launch {
            imageDownloader.downloadImage(url).onSuccess(onSuccess).onFailure(onFailure)
        }
    }
}

data class FlowUiState(val pagerData: PagerData, val nextFilterState: FilterState? = null)

data class ReadingUiState(
    val articleWithFeed: ArticleWithFeed? = null,
    val isUnread: Boolean = false,
    val isStarred: Boolean = false,
)

data class TranslationState(
    val translateState: TranslateState = TranslateState.Idle,  // 翻译状态
    val translateError: String? = null,  // 翻译错误信息
    val translationProgress: Int = 0,  // 翻译进度（当前段）
    val totalSegments: Int = 0,  // 总段数
    val originalTitle: String? = null,  // 原文标题
    val translatedTitle: String? = null,  // 译文标题
    val originalContent: ReaderState.ContentState? = null,  // 原文内容
    val translatedContent: ReaderState.ContentState? = null,  // 译文内容
)

data class ReaderState(
    val articleId: String? = null,
    val feedName: String = "",
    val title: String? = null,
    val author: String? = null,
    val link: String? = null,
    val publishedDate: Date = Date(0L),
    val content: ContentState = Loading,
    val listIndex: Int? = null,
    val nextArticle: PrefetchResult? = null,
    val previousArticle: PrefetchResult? = null,
) {
    data class PrefetchResult(val articleId: String, val index: Int)

    sealed interface ContentState {
        val text: String?
            get() {
                return when (this) {
                    is Description -> content
                    is Error -> message
                    is FullContent -> content
                    Loading -> null
                }
            }
    }

    data class FullContent(val content: String) : ContentState

    data class Description(val content: String) : ContentState

    data class Error(val message: String) : ContentState

    data object Loading : ContentState
}
