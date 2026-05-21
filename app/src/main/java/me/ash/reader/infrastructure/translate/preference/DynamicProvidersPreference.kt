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
        return decodeMap(preferences[key])
    }

    fun orderFromPreferences(preferences: Preferences): List<String> {
        val key = DataStoreKey.keys[DataStoreKey.dynamicTranslateProvidersOrder]?.key
            as? Preferences.Key<String> ?: return emptyList()
        return decodeOrder(preferences[key])
    }

    /** 同步读取（非 Composable 场景使用） */
    fun read(context: Context): Map<String, TranslateProviderConfig> {
        val raw = context.dataStore.get<String>(DataStoreKey.dynamicTranslateProviders)
        return decodeMap(raw)
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
        val out = linkedMapOf<String, TranslateProviderConfig>()
        if (siliconFlow != null &&
            (siliconFlow.apiKey.isNotBlank() || siliconFlow.enabledModels.isNotEmpty())
        ) {
            val cfg = TranslateProviderConfig(
                id = "SiliconFlow",
                kind = ProviderKind.OPENAI,
                name = "SiliconFlow",
                apiKey = siliconFlow.apiKey,
                rpm = siliconFlow.rpm,
                enabledModels = siliconFlow.enabledModels,
                baseUrl = "https://api.siliconflow.cn/v1",
                chatPath = "/chat/completions",
            )
            out[cfg.id] = cfg
        }
        if (cerebras != null &&
            (cerebras.apiKey.isNotBlank() || cerebras.enabledModels.isNotEmpty())
        ) {
            val cfg = TranslateProviderConfig(
                id = "Cerebras",
                kind = ProviderKind.OPENAI,
                name = "Cerebras",
                apiKey = cerebras.apiKey,
                rpm = cerebras.rpm,
                enabledModels = cerebras.enabledModels,
                baseUrl = "https://api.cerebras.ai/v1",
                chatPath = "/chat/completions",
            )
            out[cfg.id] = cfg
        }
        return out to out.keys.toList()
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
            var map = read(context).toMutableMap()
            var order = readOrder(context).toMutableList()

            // 首次写入时：把旧版 SiliconFlow / Cerebras 偏好种子到动态存储里
            if (map.isEmpty()) {
                val legacy = readLegacyMigrationFromDataStore(context)
                if (legacy.first.isNotEmpty()) {
                    map.putAll(legacy.first)
                    legacy.second.forEach { if (it !in order) order.add(it) }
                }
            }

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
    private fun readLegacyMigrationFromDataStore(
        context: Context,
    ): Pair<Map<String, TranslateProviderConfig>, List<String>> {
        val sfRaw = context.dataStore.get<String>(DataStoreKey.siliconFlowConfig)
        val cbRaw = context.dataStore.get<String>(DataStoreKey.cerebrasConfig)
        val sf = if (sfRaw.isNullOrBlank()) null else TranslateProviderConfig.fromJson(sfRaw)
        val cb = if (cbRaw.isNullOrBlank()) null else TranslateProviderConfig.fromJson(cbRaw)
        return buildLegacyMigration(sf, cb)
    }

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
