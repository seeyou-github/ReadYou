package me.ash.reader.infrastructure.translate.apistream

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.preference.DynamicProvidersPreference
import javax.inject.Inject

/**
 * 流式翻译服务工厂
 *
 * 根据动态供应商 id 在 [DynamicProvidersPreference] 里查到 cfg.kind，
 * 进而返回对应的 OpenAI / Google / Claude 实现。未知 id 时回退到 OpenAI。
 */
class StreamTranslateServiceFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamOpenAITranslate: StreamOpenAITranslate,
    private val streamGoogleTranslate: StreamGoogleTranslate,
    private val streamClaudeTranslate: StreamClaudeTranslate,
) {
    fun getService(serviceId: String): StreamTranslateService {
        val cfg = DynamicProvidersPreference.read(context)[serviceId]
        val kind = cfg?.kind ?: ProviderKind.OPENAI
        return when (kind) {
            ProviderKind.OPENAI -> streamOpenAITranslate
            ProviderKind.GOOGLE -> streamGoogleTranslate
            ProviderKind.CLAUDE -> streamClaudeTranslate
        }
    }
}
