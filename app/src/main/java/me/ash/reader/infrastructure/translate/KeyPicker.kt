package me.ash.reader.infrastructure.translate

import me.ash.reader.infrastructure.translate.model.LoadBalanceStrategy
import me.ash.reader.infrastructure.translate.model.TranslateProviderConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 按多 Key 负载均衡策略挑选当前请求要使用的 API Key。
 *
 * 行为：
 * - 优先在 cfg.keys 里取「启用且非空」的 Key
 * - 若多 Key 列表为空，则回退到 cfg.apiKey（单 Key 模式）
 */
object KeyPicker {

    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    fun pick(cfg: TranslateProviderConfig): String {
        val pool = cfg.keys.filter { it.enabled && it.key.isNotBlank() }
        if (pool.isEmpty()) return cfg.apiKey
        return when (cfg.loadBalance) {
            LoadBalanceStrategy.RANDOM -> pool.random().key
            LoadBalanceStrategy.ROUND_ROBIN -> {
                val counter = counters.getOrPut(cfg.id) { AtomicInteger(0) }
                val raw = counter.getAndIncrement()
                val idx = ((raw % pool.size) + pool.size) % pool.size
                pool[idx].key
            }
        }
    }
}
