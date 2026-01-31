package me.ash.reader.domain.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.paging.ItemSnapshotList
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.cachedIn
import javax.inject.Inject
import kotlin.text.trim
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.ash.reader.domain.model.article.ArticleFlowItem
import me.ash.reader.domain.model.article.mapPagingFlowItem
import me.ash.reader.domain.repository.ArticleDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider

class ArticlePagingListUseCase
@Inject
constructor(
    private val rssService: RssService,
    private val androidStringsHelper: AndroidStringsHelper,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    private val settingsProvider: SettingsProvider,
    private val filterStateUseCase: FilterStateUseCase,
    private val accountService: AccountService,
    private val feedDao: FeedDao,
    private val articleDao: ArticleDao,
) {

    private val mutablePagerFlow =
        MutableStateFlow<PagerData>(
            PagerData(filterState = filterStateUseCase.filterStateFlow.value)
        )
    val pagerFlow: StateFlow<PagerData> = mutablePagerFlow

    var itemSnapshotList by
        mutableStateOf(
            ItemSnapshotList<ArticleFlowItem>(
                placeholdersBefore = 0,
                placeholdersAfter = 0,
                items = emptyList(),
            )
        )
        private set

    val pagingDataPresenter =
        object : PagingDataPresenter<ArticleFlowItem>() {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<ArticleFlowItem>) {
                itemSnapshotList = snapshot()
            }
        }

    /**
     * 监听 itemSnapshotList 变化的 Flow
     * 修改日期：2026-02-03
     * 修改原因：为标题翻译功能提供监听文章数量变化的 Flow
     */
    val itemSnapshotListFlow = snapshotFlow { itemSnapshotList }

    init {
        applicationScope.launch(ioDispatcher) {
            filterStateUseCase.filterStateFlow
                .combine(accountService.currentAccountIdFlow) { filterState, _ ->
                    filterState
                }
                .collect { filterState ->
                    // 防御性检查：确保 filterState 不为 null
                    val currentFilterState = filterState ?: return@collect
                    val searchContent = currentFilterState.searchContent

                    mutablePagerFlow.value =
                        PagerData(
                            Pager(
                                    config = PagingConfig(pageSize = 50, enablePlaceholders = false)
                                ) {
                                    if (!searchContent.isNullOrBlank()) {
                                        rssService
                                            .get()
                                            .searchArticles(
                                                content = searchContent.trim(),
                                                groupId = currentFilterState.group?.id,
                                                feedId = currentFilterState.feed?.id,
                                                isStarred = currentFilterState.filter.isStarred(),
                                                isUnread = currentFilterState.filter.isUnread(),
                                                sortAscending =
                                                    settingsProvider.settings.flowSortUnreadArticles
                                                        .value,
                                            )
                                    } else {
                                        rssService
                                            .get()
                                            .pullArticles(
                                                groupId = currentFilterState.group?.id,
                                                feedId = currentFilterState.feed?.id,
                                                isStarred = currentFilterState.filter.isStarred(),
                                                isUnread = currentFilterState.filter.isUnread(),
                                                sortAscending =
                                                    settingsProvider.settings.flowSortUnreadArticles
                                                        .value,
                                            )
                                    }
                                }
                                .flow
                                .map { it.mapPagingFlowItem(androidStringsHelper) }
                                .cachedIn(applicationScope),
                            filterState = currentFilterState,
                        )
                }
        }
        applicationScope.launch {
            pagerFlow.collectLatest { (pager, _) ->
                pager.collectLatest { pagingDataPresenter.collectFrom(it) }
            }
        }

        // 监听文章数量变化，当文章被清空或新增时自动刷新
        observeArticleCountChanges()
    }

    /**
     * 上一次处理的文章数量，用于检测变化
     */
    private val lastArticleCount = MutableStateFlow(-1)

    /**
     * 监听当前过滤条件下的文章数量变化
     * 当文章被清空或新增时，自动触发 Pager 刷新
     */
    private fun observeArticleCountChanges() {
        applicationScope.launch(ioDispatcher) {
            filterStateUseCase.filterStateFlow
                .combine(accountService.currentAccountIdFlow) { filterState, accountId ->
                    Pair(filterState, accountId)
                }
                .collect { (filterState, accountId) ->
                    // 防御性检查：如果 accountId 为 null，跳过
                    val currentAccountId = accountId ?: return@collect

                    val articleCountFlow = when {
                        filterState.feed != null -> kotlinx.coroutines.flow.flow {
                            val count = articleDao.queryMetadataByFeedId(
                                currentAccountId, filterState.feed.id, filterState.filter.isUnread()
                            ).size
                            emit(count)
                        }
                        filterState.group != null -> kotlinx.coroutines.flow.flow {
                            val count = articleDao.queryMetadataByGroupIdWhenIsUnread(
                                currentAccountId, filterState.group.id, filterState.filter.isUnread()
                            ).size
                            emit(count)
                        }
                        else -> kotlinx.coroutines.flow.flow {
                            val count = articleDao.queryMetadataAll(currentAccountId, filterState.filter.isUnread()).size
                            emit(count)
                        }
                    }

                    articleCountFlow.collect { count ->
                        // 防御性检查：确保 lastArticleCount 已初始化
                        val lastCount = lastArticleCount.value.takeIf { it >= 0 } ?: return@collect
                        // 文章数量发生显著变化时触发刷新
                        if (lastCount != count) {
                            lastArticleCount.value = count
                            val searchContent = filterState.searchContent

                            mutablePagerFlow.value =
                                PagerData(
                                    Pager(
                                            config = PagingConfig(pageSize = 50, enablePlaceholders = false)
                                        ) {
                                            if (!searchContent.isNullOrBlank()) {
                                                rssService
                                                    .get()
                                                    .searchArticles(
                                                        content = searchContent.trim(),
                                                        groupId = filterState.group?.id,
                                                        feedId = filterState.feed?.id,
                                                        isStarred = filterState.filter.isStarred(),
                                                        isUnread = filterState.filter.isUnread(),
                                                        sortAscending =
                                                            settingsProvider.settings.flowSortUnreadArticles.value,
                                                    )
                                            } else {
                                                rssService
                                                    .get()
                                                    .pullArticles(
                                                        groupId = filterState.group?.id,
                                                        feedId = filterState.feed?.id,
                                                        isStarred = filterState.filter.isStarred(),
                                                        isUnread = filterState.filter.isUnread(),
                                                        sortAscending =
                                                            settingsProvider.settings.flowSortUnreadArticles.value,
                                                    )
                                            }
                                        }
                                        .flow
                                        .map { it.mapPagingFlowItem(androidStringsHelper) }
                                        .cachedIn(applicationScope),
                                    filterState = filterState,
                                )
                        }
                    }
                }
        }
    }
}

data class PagerData(
    val pager: Flow<PagingData<ArticleFlowItem>> = emptyFlow(),
    val filterState: FilterState = FilterState(),
)