package me.ash.reader.infrastructure.translate

/**
 * 翻译服务提供商枚举
 *
 * 定义所有可用的翻译服务
 */
enum class TranslateProvider(val serviceId: String, val serviceName: String) {
    SILICONFLOW("siliconflow", "SiliconFlow"),
    CEREBRAS("cerebras", "Cerebras");

    companion object {
        /**
         * 根据 serviceId 获取对应的 Provider
         *
         * @param serviceId 服务ID
         * @return 对应的 Provider，如果不存在则返回 SILICONFLOW
         */
        fun fromServiceId(serviceId: String): TranslateProvider {
            return values().find { it.serviceId == serviceId } ?: SILICONFLOW
        }

        /**
         * 获取所有可用的服务名称列表
         *
         * @return 服务名称列表
         */
        fun getServiceNames(): List<String> {
            return values().map { it.serviceName }
        }

        /**
         * 根据 serviceId 获取服务名称
         *
         * @param serviceId 服务ID
         * @return 服务名称
         */
        fun getServiceName(serviceId: String): String {
            return fromServiceId(serviceId).serviceName
        }
    }
}