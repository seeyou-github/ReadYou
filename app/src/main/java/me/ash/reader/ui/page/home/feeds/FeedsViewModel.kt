package me.ash.reader.ui.page.home.feeds

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.ash.reader.R
import me.ash.reader.domain.model.account.Account
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.model.general.Filter
import me.ash.reader.domain.service.AbstractRssRepository
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.service.RssService
import me.ash.reader.infrastructure.android.AndroidStringsHelper
import me.ash.reader.domain.data.DiffMapHolder
import timber.log.Timber
import me.ash.reader.domain.data.FilterState
import me.ash.reader.domain.data.FilterStateUseCase
import me.ash.reader.domain.data.GroupWithFeedsListUseCase
import me.ash.reader.domain.service.SyncWorker
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.DefaultDispatcher
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider
import javax.inject.Inject

private const val TAG = "FeedsViewModel"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedsViewModel @Inject constructor(
    private val accountService: AccountService,
    private val rssService: RssService,
    private val workManager: WorkManager,
    private val androidStringsHelper: AndroidStringsHelper,
    @DefaultDispatcher
    private val defaultDispatcher: CoroutineDispatcher,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope
    private val applicationScope: CoroutineScope,
    private val settingsProvider: SettingsProvider,
    private val diffMapHolder: DiffMapHolder,
    private val filterStateUseCase: FilterStateUseCase,
    private val groupWithFeedsListUseCase: GroupWithFeedsListUseCase,
) : ViewModel() {

    private val _feedsUiState =
        MutableStateFlow(FeedsUiState())
    val feedsUiState: StateFlow<FeedsUiState> = _feedsUiState.asStateFlow()

    // 2026-01-25: 同步进度 StateFlow（当前/总数）
    // 原因：用户反馈需要在更新订阅源时显示进度
    // 时间：2026-01-25
    private val _syncProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val syncProgress: StateFlow<Pair<Int, Int>?> = _syncProgress.asStateFlow()

    val syncWorkLiveData = workManager.getWorkInfosByTagLiveData(SyncWorker.SYNC_TAG)

    val filterStateFlow = filterStateUseCase.filterStateFlow
    val groupWithFeedsListFlow = groupWithFeedsListUseCase.groupWithFeedListFlow

    var currentJob: Job? = null

    fun sync() {
        applicationScope.launch(ioDispatcher) {
            // 2026-01-25: 手动设置初始进度为 (0, 0) 确保 UI 显示
            // 原因：用户反馈需要看到进度显示
            // 时间：2026-01-25
            Timber.tag("SyncProgress").d("ViewModel开始同步，设置初始进度为 (0, 0)")
            AbstractRssRepository.setSyncProgress(0, 0)
            Timber.tag("SyncProgress").d("调用 doSyncOneTime()")
            rssService.get().doSyncOneTime()
        }
    }

    fun cancelSync() {
        rssService.get().cancelSync()
    }

    fun commitDiffs() = diffMapHolder.commitDiffsToDb()

    fun changeFilter(filterState: FilterState) {
        filterStateUseCase.updateFilterState(filterState)
    }

    // 2026-01-22: 新增 updateGroup 方法
    // 修改原因：支持更新分组信息，包括排序
    suspend fun updateGroup(group: me.ash.reader.domain.model.group.Group) {
        accountService.updateGroup(group)
    }

    // 2026-01-27: 新增 deleteFeed 方法
    // 修改原因：支持从排序对话框删除订阅源
    suspend fun deleteFeed(feed: Feed) {
        rssService.get().deleteFeed(feed)
    }

    // 2026-01-27: 新增 updateFeed 方法
    // 修改原因：支持更新订阅源信息，包括排序
    suspend fun updateFeed(feed: me.ash.reader.domain.model.feed.Feed) {
        accountService.updateFeed(feed)
    }

    // 2026-01-28: 新增 markAllAsRead 方法
    // 修改原因：支持一键标记所有分组的所有文章为已读
    fun markAllAsRead() {
        applicationScope.launch(ioDispatcher) {
            try {
                rssService.get().markAsRead(
                    groupId = null,
                    feedId = null,
                    articleId = null,
                    before = java.util.Date(Long.MAX_VALUE),
                    isUnread = false,
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "标记所有文章为已读失败")
            }
        }
    }

    init {
        val accountFlow = accountService.currentAccountFlow
        viewModelScope.launch {
            accountFlow.collect { account ->
                _feedsUiState.update { it.copy(account = account) }
            }
        }
        // 2026-01-25: 收集同步进度
        // 原因：用户反馈需要在更新订阅源时显示进度
        // 时间：2026-01-25
        viewModelScope.launch {
            AbstractRssRepository.syncProgress.collect { progress ->
                _syncProgress.value = progress
            }
        }
        viewModelScope.launch {
            filterStateUseCase.filterStateFlow.mapLatest { it.filter }
                .combine(accountFlow) { filter, account ->
                    filter
                }
                .collect {
                    currentJob?.cancel()
                    currentJob = when (it) {
                        Filter.Unread -> pullUnreadFeeds()
                        Filter.Starred -> pullStarredFeeds()
                        else -> pullAllFeeds()
                    }
                }
        }
    }

    private fun pullAllFeeds(): Job {
        val articleCountMapFlow =
            rssService.get().pullImportant(isStarred = false, isUnread = false)

        return viewModelScope.launch {
            launch {
                articleCountMapFlow.mapLatest {
                    val sum = it.values.sum()
                    androidStringsHelper.getQuantityString(R.plurals.all_desc, sum, sum)
                }.flowOn(defaultDispatcher).collect { text ->
                    _feedsUiState.update { it.copy(importantSum = text) }
                }
            }
        }
    }

    private fun pullStarredFeeds(): Job {
        val starredCountMap = rssService.get().pullImportant(isStarred = true, isUnread = false)

        return viewModelScope.launch {
            starredCountMap.mapLatest {
                val sum = it.values.sum()
                androidStringsHelper.getQuantityString(R.plurals.starred_desc, sum, sum)
            }.flowOn(defaultDispatcher).collect { text ->
                _feedsUiState.update { it.copy(importantSum = text) }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun pullUnreadFeeds(): Job {
        val unreadCountMapFlow = rssService.get().pullImportant(isStarred = false, isUnread = true)

        return viewModelScope.launch {
            diffMapHolder.diffMapSnapshotFlow
                .combine(
                    unreadCountMapFlow
                ) { diffMap, unreadCountMap ->
                    val sum = unreadCountMap.values.sum()
                    val combinedSum =
                        sum + diffMap.values.sumOf { if (it.isUnread) 1.toInt() else -1 } // KT-46360
                    androidStringsHelper.getQuantityString(
                        R.plurals.unread_desc,
                        combinedSum,
                        combinedSum
                    )
                }.debounce(200L).flowOn(defaultDispatcher).collect { text ->
                    _feedsUiState.update { it.copy(importantSum = text) }
                }
        }
    }

//    @OptIn(ExperimentalCoroutinesApi::class)
//    fun pullFeeds(filterState: FilterState, hideEmptyGroups: Boolean) {
//        val isStarred = filterState.filter.isStarred()
//        val isUnread = filterState.filter.isUnread()
//        _feedsUiState.update {
//            val important = rssService.get().pullImportant(isStarred, isUnread)
//            it.copy(
////                importantSum = important
////                    .mapLatest {
////                        (it["sum"] ?: 0).run {
////                            androidStringsHelper.getQuantityString(
////                                when {
////                                    isStarred -> R.plurals.starred_desc
////                                    isUnread -> R.plurals.unread_desc
////                                    else -> R.plurals.all_desc
////                                },
////                                this,
////                                this
////                            )
////                        }
////                    }.flowOn(defaultDispatcher),
//                groupWithFeedList = combine(
//                    important,
//                    rssService.get().pullFeeds()
//                ) { importantMap, groupWithFeedList ->
//                    val groupIterator = groupWithFeedList.iterator()
//                    while (groupIterator.hasNext()) {
//                        val groupWithFeed = groupIterator.next()
//                        val groupImportant = importantMap[groupWithFeed.group.id] ?: 0
//                        if (hideEmptyGroups && (isStarred || isUnread) && groupImportant == 0) {
//                            groupIterator.remove()
//                            continue
//                        }
//                        groupWithFeed.group.important = groupImportant
//                        val feedIterator = groupWithFeed.feeds.iterator()
//                        while (feedIterator.hasNext()) {
//                            val feed = feedIterator.next()
//                            val feedImportant = importantMap[feed.id] ?: 0
//                            groupWithFeed.group.feeds++
//                            if (hideEmptyGroups && (isStarred || isUnread) && feedImportant == 0) {
//                                feedIterator.remove()
//                                continue
//                            }
//                            feed.important = feedImportant
//                        }
//                    }
//                    groupWithFeedList
//                }.mapLatest { list ->
//                    list.filter { (group, feeds) ->
//                        group.id != feedsUiState.value.account?.id?.getDefaultGroupId() || feeds.isNotEmpty()
//                    }
//                }.flowOn(defaultDispatcher),
//            )
//        }
//    }
}

data class FeedsUiState(
    val account: Account? = null,
    val importantSum: String = "",
    val listState: LazyListState = LazyListState(),
    val groupsVisible: SnapshotStateMap<String, Boolean> = mutableStateMapOf(),
)
