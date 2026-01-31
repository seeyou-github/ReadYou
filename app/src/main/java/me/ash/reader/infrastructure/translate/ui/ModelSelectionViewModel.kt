package me.ash.reader.infrastructure.translate.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import me.ash.reader.infrastructure.translate.ModelFetchService
import javax.inject.Inject

/**
 * ViewModel 用于注入 ModelFetchService
 */
@HiltViewModel
class ModelSelectionViewModel @Inject constructor(
    val modelFetchService: ModelFetchService
) : ViewModel()
