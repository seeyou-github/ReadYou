package me.ash.reader.infrastructure.translate.preference

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import me.ash.reader.ui.ext.DataStoreKey
import me.ash.reader.ui.ext.dataStore
import me.ash.reader.ui.ext.get
import me.ash.reader.ui.ext.put

/**
 * 动态翻译供应商存储
 *
 * 用 DataStore 中的两个 key 持久化：
 * - `dynamic_translate_providers`: `Map<id, TranslateProviderConfig>` JSON
 * - `dynamic_translate_providers_order`: `List<String>` JSON（控制 UI 顺序）
 */
val LocalDynamicProviders = compositionLocalOf<Map<String, TranslateProviderConfig>> { emptyMap() }
val LocalDynamicProvidersOrder = compositionLocalOf<List<String>> { emptyList() }

object DynamicProvidersPreference {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val mapSerializer =
        MapSerializer(String.serializer(), TranslateProviderConfig.serializer())
    private val listSerializer = ListSerializer(String.serializer())

    /**
     * 内置供应商（OpenAI 兼容协议）。
     *
     * 出现在 AI 提供商页面，用户可直接编辑（apiKey/名称/baseUrl/...）或关闭；
     * 用户编辑后会以同 id 写入 DataStore 覆盖默认值。
     * 用户点删除时，id 会被写入 [DataStoreKey.hiddenBuiltinTranslateProviders] 隐藏集。
     */
    val BUILT_IN_PROVIDERS: List<TranslateProviderConfig> = listOf(
        TranslateProviderConfig(
            id = "Cerebras",
            kind = ProviderKind.OPENAI,
            name = "Cerebras",
            baseUrl = "https://api.cerebras.ai/v1",
            chatPath = "/chat/completions",
        ),
        TranslateProviderConfig(
            id = "NVIDIA API",
            kind = ProviderKind.OPENAI,
            name = "NVIDIA API",
            baseUrl = "https://integrate.api.nvidia.com/v1",
            chatPath = "/chat/completions",
        ),
        TranslateProviderConfig(
            id = "OpenRouter",
            kind = ProviderKind.OPENAI,
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            chatPath = "/chat/completions",
        ),
        TranslateProviderConfig(
            id = "智普",
            kind = ProviderKind.OPENAI,
            name = "智普",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            chatPath = "/chat/completions",
            enabledModels = listOf(
                "GLM-4.6V-Flash",
                "GLM-4.1V-Thinking-Flash",
                "GLM-4.7-Flash",
                "GLM-Z1-Flash",
                "GLM-4-Flash-250414",
                "GLM-4-Flash",
            ),
        ),
        TranslateProviderConfig(
            id = "SiliconFlow",
            kind = ProviderKind.OPENAI,
            name = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn/v1",
            chatPath = "/chat/completions",
        ),
    )

    private val BUILT_IN_BY_ID: Map<String, TranslateProviderConfig> =
        BUILT_IN_PROVIDERS.associateBy { it.id }

    fun isBuiltIn(id: String): Boolean = BUILT_IN_BY_ID.containsKey(id)

    fun builtInConfig(id: String): TranslateProviderConfig? = BUILT_IN_BY_ID[id]

    fun decodeMap(text: String?): Map<String, TranslateProviderConfig> {
        if (text.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(mapSerializer, text)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun decodeOrder(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun fromPreferences(preferences: Preferences): Map<String, TranslateProviderConfig> {
        val key = DataStoreKey.keys[DataStoreKey.dynamicTranslateProviders]?.key
            as? Preferences.Key<String> ?: return emptyMap()
        val siliconFlow = preferences.legacyProvider(DataStoreKey.siliconFlowConfig)
        val cerebras = preferences.legacyProvider(DataStoreKey.cerebrasConfig)
        return filterLegacySeededProviders(decodeMap(preferences[key]), siliconFlow, cerebras)
    }

    fun orderFromPreferences(preferences: Preferences): List<String> {
        val key = DataStoreKey.keys[DataStoreKey.dynamicTranslateProvidersOrder]?.key
            as? Preferences.Key<String> ?: return emptyList()
        return decodeOrder(preferences[key])
    }

    fun hiddenBuiltInFromPreferences(preferences: Preferences): Set<String> {
        val key = DataStoreKey.keys[DataStoreKey.hiddenBuiltinTranslateProviders]?.key
            as? Preferences.Key<String> ?: return emptySet()
        return decodeOrder(preferences[key]).toSet()
    }

    fun readHiddenBuiltIns(context: Context): Set<String> {
        val raw = context.dataStore.get<String>(DataStoreKey.hiddenBuiltinTranslateProviders)
        return decodeOrder(raw).toSet()
    }

    fun hideBuiltIn(context: Context, scope: CoroutineScope, id: String) {
        if (!isBuiltIn(id)) return
        scope.launch(Dispatchers.IO) {
            val current = readHiddenBuiltIns(context).toMutableSet()
            if (current.add(id)) {
                context.dataStore.put(
                    DataStoreKey.hiddenBuiltinTranslateProviders,
                    json.encodeToString(listSerializer, current.toList()),
                )
            }
        }
    }

    fun unhideBuiltIn(context: Context, scope: CoroutineScope, id: String) {
        scope.launch(Dispatchers.IO) {
            val current = readHiddenBuiltIns(context).toMutableSet()
            if (current.remove(id)) {
                context.dataStore.put(
                    DataStoreKey.hiddenBuiltinTranslateProviders,
                    json.encodeToString(listSerializer, current.toList()),
                )
            }
        }
    }

    /**
     * 给定持久化映射和隐藏集合，合并出最终 UI 视图：内置项 + 旧版迁移 + 用户保存项。
     * 同 id 时优先级：用户保存 > 旧版迁移 > 内置。
     */
    fun mergeBuiltIns(
        persisted: Map<String, TranslateProviderConfig>,
        legacy: Map<String, TranslateProviderConfig>,
        hidden: Set<String>,
    ): Map<String, TranslateProviderConfig> {
        val out = linkedMapOf<String, TranslateProviderConfig>()
        BUILT_IN_PROVIDERS.forEach { cfg ->
            if (cfg.id !in hidden && cfg.id !in persisted) {
                out[cfg.id] = cfg
            }
        }
        persisted.forEach { (id, cfg) -> out[id] = cfg }
        return out
    }

    /**
     * 合并最终顺序：用户已保存顺序 > 旧版迁移顺序 > 内置默认顺序，缺失项追加末尾。
     */
    fun mergeOrder(
        persistedOrder: List<String>,
        legacyOrder: List<String>,
        finalMap: Map<String, TranslateProviderConfig>,
    ): List<String> {
        val seen = linkedSetOf<String>()
        persistedOrder.forEach { if (it in finalMap) seen.add(it) }
        BUILT_IN_PROVIDERS.forEach { if (it.id in finalMap) seen.add(it.id) }
        finalMap.keys.forEach { seen.add(it) }
        return seen.toList()
    }

    /** 同步读取（非 Composable 场景使用） */
    fun read(context: Context): Map<String, TranslateProviderConfig> {
        val raw = context.dataStore.get<String>(DataStoreKey.dynamicTranslateProviders)
        val siliconFlow = context.legacyProvider(DataStoreKey.siliconFlowConfig)
        val cerebras = context.legacyProvider(DataStoreKey.cerebrasConfig)
        return filterLegacySeededProviders(decodeMap(raw), siliconFlow, cerebras)
    }

    fun readOrder(context: Context): List<String> {
        val raw = context.dataStore.get<String>(DataStoreKey.dynamicTranslateProvidersOrder)
        return decodeOrder(raw)
    }

    /**
     * 从历史的 SiliconFlow / Cerebras 偏好生成迁移后的 Map。
     *
     * 仅返回真正配置过（apiKey 或 enabledModels 非空）的项。
     */
    fun buildLegacyMigration(
        siliconFlow: TranslateProviderConfig?,
        cerebras: TranslateProviderConfig?,
    ): Pair<Map<String, TranslateProviderConfig>, List<String>> {
        return emptyMap<String, TranslateProviderConfig>() to emptyList()
    }

    private fun Preferences.legacyProvider(keyName: String): TranslateProviderConfig? {
        val key = DataStoreKey.keys[keyName]?.key as? Preferences.Key<String> ?: return null
        val raw = this[key].takeUnless { it.isNullOrBlank() } ?: return null
        return TranslateProviderConfig.fromJson(raw)
    }

    private fun Context.legacyProvider(keyName: String): TranslateProviderConfig? {
        val raw = dataStore.get<String>(keyName).takeUnless { it.isNullOrBlank() } ?: return null
        return TranslateProviderConfig.fromJson(raw)
    }

    private fun filterLegacySeededProviders(
        map: Map<String, TranslateProviderConfig>,
        siliconFlow: TranslateProviderConfig?,
        cerebras: TranslateProviderConfig?,
    ): Map<String, TranslateProviderConfig> {
        if (map.isEmpty()) return map
        val filtered = map.toMutableMap()
        removeLegacySeededProvider(filtered, "SiliconFlow", siliconFlow)
        removeLegacySeededProvider(filtered, "Cerebras", cerebras)
        return filtered
    }

    private fun removeLegacySeededProvider(
        map: MutableMap<String, TranslateProviderConfig>,
        id: String,
        legacy: TranslateProviderConfig?,
    ) {
        if (legacy == null) return
        val cfg = map[id] ?: return
        val hasLegacyData = legacy.apiKey.isNotBlank() || legacy.enabledModels.isNotEmpty()
        if (!hasLegacyData) return
        if (cfg.apiKey == legacy.apiKey && cfg.enabledModels == legacy.enabledModels) {
            map.remove(id)
        }
    }

    fun putMap(
        context: Context,
        scope: CoroutineScope,
        map: Map<String, TranslateProviderConfig>,
    ) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(
                DataStoreKey.dynamicTranslateProviders,
                json.encodeToString(mapSerializer, map),
            )
        }
    }

    fun putOrder(
        context: Context,
        scope: CoroutineScope,
        order: List<String>,
    ) {
        scope.launch(Dispatchers.IO) {
            context.dataStore.put(
                DataStoreKey.dynamicTranslateProvidersOrder,
                json.encodeToString(listSerializer, order),
            )
        }
    }

    /** 写入单个供应商配置（会同步更新 order：新增项放头部） */
    fun put(context: Context, scope: CoroutineScope, cfg: TranslateProviderConfig) {
        scope.launch(Dispatchers.IO) {
            val map = read(context).toMutableMap()
            val order = readOrder(context).toMutableList()

            // 首次写入时：把旧版 SiliconFlow / Cerebras 偏好种子到动态存储里
            val isNew = !map.containsKey(cfg.id)
            map[cfg.id] = cfg
            context.dataStore.put(
                DataStoreKey.dynamicTranslateProviders,
                json.encodeToString(mapSerializer, map),
            )
            if (isNew) {
                order.remove(cfg.id)
                order.add(0, cfg.id)
            }
            context.dataStore.put(
                DataStoreKey.dynamicTranslateProvidersOrder,
                json.encodeToString(listSerializer, order),
            )
        }
    }

    /** 从 DataStore 直接读取旧版 SiliconFlow / Cerebras 偏好并迁移 */
    fun remove(context: Context, scope: CoroutineScope, id: String) {
        scope.launch(Dispatchers.IO) {
            val map = read(context).toMutableMap()
            map.remove(id)
            context.dataStore.put(
                DataStoreKey.dynamicTranslateProviders,
                json.encodeToString(mapSerializer, map),
            )
            val order = readOrder(context).toMutableList()
            order.remove(id)
            context.dataStore.put(
                DataStoreKey.dynamicTranslateProvidersOrder,
                json.encodeToString(listSerializer, order),
            )
        }
    }

    /**
     * 生成与现有 id 不冲突的唯一名。
     *
     * 复用 Flutter add_provider_dialog.dart 的 uniqueKey 逻辑：
     * - 用户名同 prefix → "Prefix - 1" 起步递增
     * - 否则使用 "Prefix - display"，重复则加 "(2)" "(3)" ...
     */
    fun uniqueKey(existing: Set<String>, prefix: String, display: String): String {
        if (display.equals(prefix, ignoreCase = true)) {
            var i = 1
            var candidate = "$prefix - $i"
            while (existing.contains(candidate)) {
                i++
                candidate = "$prefix - $i"
            }
            return candidate
        }
        val base = "$prefix - $display"
        if (!existing.contains(base)) return base
        var i = 2
        var candidate = "$base ($i)"
        while (existing.contains(candidate)) {
            i++
            candidate = "$base ($i)"
        }
        return candidate
    }

    /** 默认 Base URL */
    fun defaultBaseUrl(kind: ProviderKind): String = when (kind) {
        ProviderKind.OPENAI -> "https://api.openai.com/v1"
        ProviderKind.GOOGLE -> "https://generativelanguage.googleapis.com/v1beta"
        ProviderKind.CLAUDE -> "https://api.anthropic.com/v1"
    }
}
