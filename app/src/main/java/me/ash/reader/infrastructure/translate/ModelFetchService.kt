package me.ash.reader.infrastructure.translate

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.di.UserAgentInterceptor
import me.ash.reader.infrastructure.translate.model.ModelInfo
import me.ash.reader.infrastructure.translate.model.ProviderKind
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型获取服务
 *
 * 根据 [TranslateProviderConfig.kind] 选择对应供应商的 models 接口。
 */
@Singleton
class ModelFetchService @Inject constructor() {

    companion object {
        private const val TAG = "ModelFetchService"

        private val client by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(UserAgentInterceptor)
                .build()
        }

        private val gson = Gson()
    }

    /**
     * 拉取指定供应商配置下可用的模型列表。
     *
     * 多 Key 模式下使用 [KeyPicker] 挑选当前可用 Key。
     */
    suspend fun fetchModels(cfg: TranslateProviderConfig): Result<List<ModelInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = KeyPicker.pick(cfg)
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("API Key 未配置"))
                }

                val request = when (cfg.kind) {
                    ProviderKind.OPENAI -> {
                        val base = cfg.baseUrl.trimEnd('/')
                            .ifBlank { "https://api.openai.com/v1" }
                        Request.Builder()
                            .url("$base/models")
                            .addHeader("Authorization", "Bearer $apiKey")
                            .get().build()
                    }
                    ProviderKind.GOOGLE -> {
                        val base = cfg.baseUrl.trimEnd('/')
                            .ifBlank { "https://generativelanguage.googleapis.com/v1beta" }
                        Request.Builder()
                            .url("$base/models?key=$apiKey")
                            .get().build()
                    }
                    ProviderKind.CLAUDE -> {
                        val base = cfg.baseUrl.trimEnd('/')
                            .ifBlank { "https://api.anthropic.com/v1" }
                        Request.Builder()
                            .url("$base/models")
                            .addHeader("x-api-key", apiKey)
                            .addHeader("anthropic-version", "2023-06-01")
                            .get().build()
                    }
                }

                Timber.d("[$TAG] GET ${request.url.host}${request.url.encodedPath}")

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Timber.e("[$TAG] HTTP ${response.code}: $errorBody")
                    return@withContext Result.failure(
                        Exception("获取模型列表失败: ${response.code} ${response.message}")
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext Result.failure(Exception("API响应为空"))

                val models = when (cfg.kind) {
                    ProviderKind.OPENAI -> parseOpenAIModels(body)
                    ProviderKind.GOOGLE -> parseGoogleModels(body)
                    ProviderKind.CLAUDE -> parseClaudeModels(body)
                }

                Result.success(models.sortedBy { it.id })
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] fetchModels 异常")
                Result.failure(e)
            }
        }
    }

    private fun parseOpenAIModels(body: String): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        try {
            val json = gson.fromJson(body, JsonObject::class.java)
            val data = json.getAsJsonArray("data") ?: return out
            data.forEach { el ->
                val id = el.asJsonObject.get("id")?.asString ?: return@forEach
                if (isChatModel(id)) out.add(ModelInfo(id = id, name = formatModelName(id)))
            }
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] OpenAI 模型列表解析失败")
        }
        return out
    }

    private fun parseGoogleModels(body: String): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        try {
            val json = gson.fromJson(body, JsonObject::class.java)
            val data = json.getAsJsonArray("models") ?: return out
            data.forEach { el ->
                val obj = el.asJsonObject
                val nameRaw = obj.get("name")?.asString ?: return@forEach
                val id = nameRaw.removePrefix("models/")
                val supportedMethods = obj.getAsJsonArray("supportedGenerationMethods")
                val supportsGen = supportedMethods?.any {
                    it.asString == "generateContent" || it.asString == "streamGenerateContent"
                } ?: true
                if (!supportsGen) return@forEach
                out.add(ModelInfo(id = id, name = formatModelName(id)))
            }
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Google 模型列表解析失败")
        }
        return out
    }

    private fun parseClaudeModels(body: String): List<ModelInfo> {
        val out = mutableListOf<ModelInfo>()
        try {
            val json = gson.fromJson(body, JsonObject::class.java)
            val data = json.getAsJsonArray("data") ?: return out
            data.forEach { el ->
                val obj = el.asJsonObject
                val id = obj.get("id")?.asString ?: return@forEach
                val display = obj.get("display_name")?.asString ?: formatModelName(id)
                out.add(ModelInfo(id = id, name = display))
            }
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] Claude 模型列表解析失败")
        }
        return out
    }

    private fun isChatModel(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return !lower.contains("embedding") &&
            !lower.contains("dall-e") &&
            !lower.contains("tts") &&
            !lower.contains("whisper") &&
            !lower.contains("moderation")
    }

    private fun formatModelName(modelId: String): String = when {
        modelId.contains("qwen") -> "通义千问 ${modelId.removePrefix("qwen-")}"
        modelId.contains("glm") -> "智谱 GLM-${modelId.removePrefix("glm-").uppercase()}"
        modelId.contains("gpt") -> modelId.uppercase()
        else -> modelId
    }
}
