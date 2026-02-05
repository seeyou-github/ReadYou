package me.ash.reader.plugin.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.ash.reader.domain.service.AccountService
import me.ash.reader.domain.repository.FeedDao
import me.ash.reader.plugin.PluginFeedManager
import me.ash.reader.plugin.PluginRule
import me.ash.reader.plugin.PluginRuleDao
import me.ash.reader.plugin.PluginRuleTransferService

@HiltViewModel
class PluginListViewModel @Inject constructor(
    private val pluginRuleDao: PluginRuleDao,
    private val accountService: AccountService,
    private val pluginFeedManager: PluginFeedManager,
    private val feedDao: FeedDao,
    private val pluginRuleTransferService: PluginRuleTransferService,
) : ViewModel() {

    private val accountId = accountService.getCurrentAccountId()

    val rules: StateFlow<List<PluginRule>> =
        pluginRuleDao.flowAll(accountId)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteRule(rule: PluginRule) {
        viewModelScope.launch {
            Log.d(TAG, "delete rule ${rule.id}")
            pluginRuleDao.delete(rule)
            pluginFeedManager.removeFeed(rule)
        }
    }

    fun deleteRules(ruleIds: Set<String>) {
        viewModelScope.launch {
            val targets = pluginRuleDao.queryAll(accountId).filter { ruleIds.contains(it.id) }
            targets.forEach { rule ->
                Log.d(TAG, "delete rule ${rule.id}")
                pluginRuleDao.delete(rule)
                pluginFeedManager.removeFeed(rule)
            }
        }
    }

    fun toggleRule(rule: PluginRule, enabled: Boolean) {
        viewModelScope.launch {
            Log.d(TAG, "toggle rule ${rule.id} -> $enabled")
            val updated = rule.copy(isEnabled = enabled, updatedAt = System.currentTimeMillis())
            pluginRuleDao.insert(updated)
            if (enabled) {
                pluginFeedManager.ensureFeed(updated)
            } else {
                pluginFeedManager.removeFeed(updated)
            }
        }
    }

    suspend fun exportRule(rule: PluginRule): ExportPayload? {
        val pluginUrl = pluginFeedManager.buildPluginUrl(rule.id)
        val feed = feedDao.queryByLink(rule.accountId, pluginUrl).firstOrNull() ?: return null
        val json = pluginRuleTransferService.exportRule(rule, feed)
        val baseName = (rule.name.ifBlank { rule.subscribeUrl }).replace("/", "_")
        return ExportPayload(
            fileName = "${baseName}.json",
            content = json,
        )
    }

    suspend fun exportRulesPayloads(rules: List<PluginRule>): List<ExportPayload> {
        if (rules.isEmpty()) return emptyList()
        val result = mutableListOf<ExportPayload>()
        for (rule in rules) {
            val export = exportRule(rule) ?: continue
            result.add(export)
        }
        return result
    }

    fun importRule(json: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            pluginRuleTransferService.importRule(json)
                .onSuccess { onResult("本地规则导入成功") }
                .onFailure { onResult("本地规则导入失败：${it.message}") }
        }
    }

    companion object {
        private const val TAG = "PluginListVM"
    }
}

data class ExportPayload(
    val fileName: String,
    val content: String,
)
