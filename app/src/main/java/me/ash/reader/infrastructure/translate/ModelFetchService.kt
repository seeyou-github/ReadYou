package me.ash.reader.infrastructure.translate

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ash.reader.infrastructure.translate.model.ModelInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型获取服务
 *
 * 用于从翻译提供商 API 获取可用模型列表
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
                .build()
        }

        private val gson = Gson()
    }

    /**
     * 获取指定提供商的模型列表
     *
     * @param providerId 提供商ID (siliconflow 或 cerebras)
     * @param apiKey API密钥
     * @return Result<List<ModelInfo>> 成功返回模型列表，失败返回错误信息
     */
    suspend fun fetchModels(providerId: String, apiKey: String): Result<List<ModelInfo>> {
        return withContext(Dispatchers.IO) {
            try {
                val provider = TranslateProviders.getById(providerId)
                    ?: return@withContext Result.failure(Exception("未知的提供商: $providerId"))

                Timber.d("[$TAG] 开始获取模型列表: ${provider.name}")
                Timber.d("[$TAG] API URL: ${provider.modelsUrl}")

                val request = Request.Builder()
                    .url(provider.modelsUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Timber.e("[$TAG] API请求失败: ${response.code} ${response.message}, 响应: $errorBody")
                    return@withContext Result.failure(
                        Exception("获取模型列表失败: ${response.code} ${response.message}")
                    )
                }

                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(Exception("API响应为空"))

                Timber.d("[$TAG] API响应成功，响应长度: ${responseBody.length}")

                // 解析模型列表
                val models = parseModels(responseBody)

                Timber.d("[$TAG] 解析完成，共获取 ${models.size} 个模型")

                Result.success(models)
            } catch (e: Exception) {
                Timber.e(e, "[$TAG] 获取模型列表异常")
                Result.failure(e)
            }
        }
    }

    /**
     * 解析 OpenAI 格式的模型列表响应
     *
     * @param responseBody JSON响应字符串
     * @return 模型信息列表
     */
    private fun parseModels(responseBody: String): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()

        try {
            val jsonObject = gson.fromJson(responseBody, JsonObject::class.java)
            val dataArray = jsonObject.getAsJsonArray("data")

            if (dataArray != null) {
                dataArray.forEach { element ->
                    val modelObject = element.asJsonObject
                    val id = modelObject.get("id")?.asString ?: return@forEach

                    // 过滤掉嵌入模型和图像模型，只保留聊天模型
                    if (isChatModel(id)) {
                        val model = ModelInfo(
                            id = id,
                            name = formatModelName(id),
                            description = null
                        )
                        models.add(model)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[$TAG] 解析模型列表失败")
        }

        return models.sortedBy { it.id }
    }

    /**
     * 判断是否为聊天模型
     *
     * 过滤掉嵌入模型(embedding)和图像模型(vision/dall-e)
     *
     * @param modelId 模型ID
     * @return 是否为聊天模型
     */
    private fun isChatModel(modelId: String): Boolean {
        val lowerId = modelId.lowercase()
        return !lowerId.contains("embedding") &&
                !lowerId.contains("dall-e") &&
                !lowerId.contains("tts") &&
                !lowerId.contains("whisper") &&
                !lowerId.contains("moderation")
    }

    /**
     * 格式化模型名称
     *
     * 将模型ID转换为更易读的名称
     *
     * @param modelId 模型ID
     * @return 格式化后的名称
     */
    private fun formatModelName(modelId: String): String {
        return when {
            modelId.contains("qwen") -> {
                val version = modelId.replace("qwen-", "")
                "通义千问 $version"
            }
            modelId.contains("glm") -> {
                val version = modelId.replace("glm-", "").uppercase()
                "智谱 GLM-$version"
            }
            modelId.contains("gpt") -> {
                modelId.uppercase()
            }
            else -> modelId
        }
    }
}
