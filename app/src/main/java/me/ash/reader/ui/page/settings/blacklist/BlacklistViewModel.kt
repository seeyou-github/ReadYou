package me.ash.reader.ui.page.settings.blacklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.ash.reader.domain.model.blacklist.BlacklistKeyword
import me.ash.reader.domain.model.feed.Feed
import me.ash.reader.domain.repository.BlacklistKeywordDao
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.di.MainDispatcher
import me.ash.reader.infrastructure.preference.BlacklistPreference
import javax.inject.Inject

/**
 * 2026-01-24: 关键词黑名单 ViewModel
 * 管理关键词的增删改查操作
 * 与账户无关，仅和订阅源相关
 * 支持多订阅源匹配
 */
@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val blacklistKeywordDao: BlacklistKeywordDao,
    private val feedDao: FeedDao,
    @IODispatcher
    private val ioDispatcher: CoroutineDispatcher,
    @MainDispatcher
    private val mainDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlacklistUiState())
    val uiState: StateFlow<BlacklistUiState> = _uiState.asStateFlow()

    /**
     * 所有关键词列表
     */
    val keywords: StateFlow<List<BlacklistKeyword>> = blacklistKeywordDao.getAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // 加载所有订阅源（不限制账户）
        viewModelScope.launch(ioDispatcher) {
            val feedList = feedDao.queryAllNoAccount()
            _uiState.update { it.copy(availableFeeds = feedList) }
        }
    }

    /**
     * 添加关键词
     * @param keyword 关键词内容
     * @param selectedFeedUrls 选中的作用范围（订阅源URL集合），空集合表示全部订阅源
     */
    fun addKeyword(keyword: String, selectedFeedUrls: Set<String> = emptySet()) {
        if (keyword.isBlank()) return

        viewModelScope.launch(ioDispatcher) {
            val feedUrls = selectedFeedUrls.toList().takeIf { it.isNotEmpty() }
            val feedNames = feedUrls?.mapNotNull { url ->
                _uiState.value.availableFeeds.find { it.url == url }?.name
            }
            BlacklistPreference.addKeyword(
                dao = blacklistKeywordDao,
                keyword = keyword,
                feedUrls = feedUrls,
                feedNames = feedNames,
            )
            withContext(mainDispatcher) {
                // 清空输入框和多选
                _uiState.update { it.copy(newKeyword = "", selectedFeedUrls = emptySet()) }
            }
        }
    }

    /**
     * 删除关键词
     * @param id 关键词ID
     */
    fun deleteKeyword(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            BlacklistPreference.deleteKeyword(blacklistKeywordDao, id)
        }
    }

    /**
     * 切换关键词的启用状态
     * @param id 关键词ID
     */
    fun toggleKeywordEnabled(id: Int) {
        viewModelScope.launch(ioDispatcher) {
            BlacklistPreference.toggleEnabled(blacklistKeywordDao, id)
        }
    }

    /**
     * 设置关键词的启用状态
     * @param id 关键词ID
     * @param enabled 是否启用
     */
    fun setKeywordEnabled(id: Int, enabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            BlacklistPreference.setEnabled(blacklistKeywordDao, id, enabled)
        }
    }

    /**
     * 更新新关键词输入
     */
    fun updateNewKeyword(keyword: String) {
        _uiState.update { it.copy(newKeyword = keyword) }
    }

    /**
     * 切换选择订阅源
     */
    fun toggleFeedSelection(feedUrl: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedFeedUrls.contains(feedUrl)) {
                state.selectedFeedUrls - feedUrl
            } else {
                state.selectedFeedUrls + feedUrl
            }
            state.copy(selectedFeedUrls = newSelection)
        }
    }

    /**
     * 清空订阅源选择
     */
    fun clearFeedSelection() {
        _uiState.update { it.copy(selectedFeedUrls = emptySet()) }
    }
}

/**
 * 2026-01-24: 关键词黑名单页面状态
 */
data class BlacklistUiState(
    val newKeyword: String = "",                    // 新输入的关键词
    val selectedFeedUrls: Set<String> = emptySet(), // 选中的作用范围（订阅源URL集合）
    val availableFeeds: List<Feed> = emptyList(),   // 可选的订阅源列表
)