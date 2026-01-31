package me.ash.reader.infrastructure.translate.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.translate.model.TranslateModelConfig

import me.ash.reader.infrastructure.translate.preference.QuickTranslateModelPreference
import me.ash.reader.infrastructure.translate.preference.TranslateServiceIdPreference
import me.ash.reader.infrastructure.preference.SettingsProvider
import javax.inject.Inject

/**
 * AI翻译设置页面 ViewModel
 */
@HiltViewModel
class AITranslationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsProvider: SettingsProvider
) : ViewModel() {

    private val _quickModelConfig = MutableStateFlow<TranslateModelConfig?>(null)
    val quickModelConfig: StateFlow<TranslateModelConfig?> = _quickModelConfig.asStateFlow()

    private val _longPressModelConfig = MutableStateFlow<TranslateModelConfig?>(null)
    val longPressModelConfig: StateFlow<TranslateModelConfig?> = _longPressModelConfig.asStateFlow()

    init {
        viewModelScope.launch {
            settingsProvider.settingsFlow.collect { settings ->
                _quickModelConfig.value = settings.quickTranslateModel
                _longPressModelConfig.value = settings.longPressTranslateModel
            }
        }
    }

    /**
     * 保存快速翻译模型配置
     * 同时更新翻译服务提供商
     */
    fun saveQuickModel(config: TranslateModelConfig) {
        QuickTranslateModelPreference.put(context, viewModelScope, config)
        // 同时更新翻译服务提供商
        TranslateServiceIdPreference.put(context, viewModelScope, config.provider)
    }


}
