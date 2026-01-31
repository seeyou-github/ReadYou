package me.ash.reader.infrastructure.translate.apistream

import javax.inject.Inject

/**
 * 流式翻译服务工厂
 * 根据 serviceId 返回对应的流式翻译服务
 */
class StreamTranslateServiceFactory @Inject constructor(
    private val streamSiliconFlowTranslate: StreamSiliconFlowTranslate,
    private val streamCerebrasTranslate: StreamCerebrasTranslate
) {
    fun getService(serviceId: String): StreamTranslateService {
        return when (serviceId) {
            "siliconflow" -> streamSiliconFlowTranslate
            "cerebras" -> streamCerebrasTranslate
            else -> streamSiliconFlowTranslate
        }
    }
}