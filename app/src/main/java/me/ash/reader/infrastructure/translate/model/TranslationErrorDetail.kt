package me.ash.reader.infrastructure.translate.model

import com.google.gson.JsonParser

/**
 * 翻译错误详情
 *
 * 存储API返回的完整错误信息
 */
data class TranslationErrorDetail(
    val message: String,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null,
    val httpCode: Int? = null,
    val rawResponse: String? = null
) {
    /**
     * 获取格式化的错误信息用于显示
     */
    fun getFormattedMessage(): String {
        val sb = StringBuilder()
        
        // 主要错误信息
        sb.appendLine("错误: $message")
        
        // HTTP状态码
        httpCode?.let {
            sb.appendLine("HTTP状态: $it")
        }
        
        // 错误类型
        type?.let {
            sb.appendLine("类型: $it")
        }
        
        // 错误代码
        code?.let {
            sb.appendLine("代码: $it")
        }
        
        // 参数
        param?.let {
            sb.appendLine("参数: $it")
        }
        
        // 原始响应（如果存在且与message不同）
        rawResponse?.let { raw ->
            if (raw != message && raw.length > message.length) {
                sb.appendLine()
                sb.appendLine("原始响应:")
                sb.appendLine(raw)
            }
        }
        
        return sb.toString().trim()
    }
    
    /**
     * 获取简洁的错误信息（用于Toast等短显示）
     */
    fun getShortMessage(): String {
        return if (message.length > 50) {
            message.take(50) + "..."
        } else {
            message
        }
    }
    
    companion object {
        /**
         * 从JSON响应解析错误信息
         */
        fun fromJson(json: String, httpCode: Int? = null): TranslationErrorDetail {
            return try {
                val jsonObject = JsonParser.parseString(json).asJsonObject
                TranslationErrorDetail(
                    message = jsonObject.get("message")?.asString ?: json,
                    type = jsonObject.get("type")?.asString,
                    param = jsonObject.get("param")?.asString,
                    code = jsonObject.get("code")?.asString,
                    httpCode = httpCode,
                    rawResponse = json
                )
            } catch (e: Exception) {
                // 解析失败时返回原始文本
                TranslationErrorDetail(
                    message = json,
                    httpCode = httpCode,
                    rawResponse = json
                )
            }
        }
        
        /**
         * 创建简单的错误信息
         */
        fun fromMessage(message: String, httpCode: Int? = null): TranslationErrorDetail {
            return TranslationErrorDetail(
                message = message,
                httpCode = httpCode
            )
        }
    }
}
