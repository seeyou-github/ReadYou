package me.ash.reader.infrastructure.translate

import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 全局RPM（每分钟请求数）限制管理器
 *
 * 功能：
 * - 单例模式，跨文章共享RPM计数
 * - 滑动窗口算法实现限流
 * - 达到限制时等待下一分钟开始
 * - 提供Toast提示回调（在第一批次需要等待时）
 *
 * @author iFlow CLI
 * @date 2026-01-31
 */
object RpmRateLimitManager {
    private const val TAG = "RpmRateLimitManager"

    /** 滑动窗口大小（毫秒） */
    private const val WINDOW_SIZE_MS = 60_000L

    /** 限速锁 */
    private val lock = ReentrantLock()

    /** 滑动窗口：存储请求时间戳 */
    private val requestTimestamps = ConcurrentLinkedQueue<Long>()

    /** 达到RPM限制时的回调（用于Toast提示） */
    var onLimitReached: (() -> Unit)? = null

    /**
     * 请求许可（阻塞直到获得许可）
     *
     * @param rpm 每分钟最大请求数
     * @param showToast 是否在达到限制时显示Toast提示
     * @return Pair<是否需要等待, 等待时间毫秒>
     */
    suspend fun waitForPermission(
        rpm: Int,
        showToast: Boolean = false
    ): Pair<Boolean, Long> {
        val startTime = System.currentTimeMillis()
        val waitTime = checkWaitTime(rpm)

        if (waitTime > 0) {
            Timber.d("[$TAG] 🚫 达到RPM限制 ($rpm)，需要等待 ${waitTime}ms")

            if (showToast) {
                Timber.d("[$TAG] 触发RPM限制提示回调")
                onLimitReached?.invoke()
            }

            delay(waitTime)
            val actualWaitTime = System.currentTimeMillis() - startTime
            Timber.d("[$TAG] ⏱️ 等待完成，实际等待 ${actualWaitTime}ms")
            return Pair(true, actualWaitTime)
        }

        // 记录请求时间戳
        recordRequest()
        return Pair(false, 0L)
    }

    /**
     * 检查是否需要等待（非阻塞）
     *
     * @param rpm 每分钟最大请求数
     * @return 需要等待的时间（毫秒），0表示不需要等待
     */
    fun checkWaitTime(rpm: Int): Long {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_SIZE_MS

        return lock.withLock {
            // 清理过期的请求记录（超过1分钟的）
            while (requestTimestamps.peek() != null && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll()
            }

            // 检查是否超过限制
            val requestCount = requestTimestamps.size
            if (requestCount >= rpm) {
                // 计算需要等待的时间
                val earliestTimestamp = requestTimestamps.peek() ?: return@withLock 0L
                val waitTime = (earliestTimestamp + WINDOW_SIZE_MS) - now
                return@withLock if (waitTime > 0) waitTime else 0L
            }

            0L
        }
    }

    /**
     * 记录请求时间戳
     */
    private fun recordRequest() {
        val now = System.currentTimeMillis()
        lock.withLock {
            // 清理过期的请求记录
            val windowStart = now - WINDOW_SIZE_MS
            while (requestTimestamps.peek() != null && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll()
            }

            // 记录当前请求
            requestTimestamps.add(now)

            val requestCount = requestTimestamps.size
            Timber.d("[$TAG] 记录请求，当前窗口请求数: $requestCount")
        }
    }

    /**
     * 获取当前窗口内的请求数
     */
    fun getCurrentRequestCount(): Int {
        val now = System.currentTimeMillis()
        val windowStart = now - WINDOW_SIZE_MS

        return lock.withLock {
            // 清理过期的请求记录
            while (requestTimestamps.peek() != null && requestTimestamps.peek() < windowStart) {
                requestTimestamps.poll()
            }

            requestTimestamps.size
        }
    }

    /**
     * 重置所有计数（用于测试）
     */
    fun reset() {
        lock.withLock {
            requestTimestamps.clear()
            Timber.d("[$TAG] 重置RPM计数")
        }
    }
}