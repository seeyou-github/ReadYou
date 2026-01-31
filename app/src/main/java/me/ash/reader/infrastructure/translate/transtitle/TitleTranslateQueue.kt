package me.ash.reader.infrastructure.translate.transtitle

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TitleTranslateQueue"

/**
 * 标题翻译任务
 *
 * 修改日期：2026-02-03
 * 修改原因：实现翻译队列管理，避免并发翻译
 */
data class TitleTranslateTask(
    val feedId: String,
    val feedName: String,
    val triggerSource: String
)

/**
 * 标题翻译队列管理类
 *
 * 使用 Channel 实现队列，确保同一时间只有一个翻译任务执行
 * 支持最大队列大小限制和去重
 *
 * 创建日期：2026-02-03
 * 修改原因：实现翻译队列管理，避免并发翻译和重复翻译
 */
@Singleton
class TitleTranslateQueue @Inject constructor(
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    // 队列容量
    private val queueCapacity = 5

    // 翻译任务队列
    private val taskChannel = Channel<TitleTranslateTask>(capacity = queueCapacity)

    // 当前正在处理的 Feed ID
    private var currentProcessingFeedId: String? = null

    // 当前处理的 Job
    private var currentJob: Job? = null

    // 正在处理的任务列表（用于去重）
    private val processingTasks = mutableSetOf<String>()

    init {
        // 启动队列处理器
        startQueueProcessor()
        Timber.tag(TAG).d("翻译队列已初始化，队列容量：$queueCapacity")
    }

    /**
     * 添加翻译任务到队列
     *
     * @param feedId Feed ID
     * @param feedName Feed 名称
     * @param triggerSource 触发来源（用于日志）
     * @return 是否成功添加到队列
     */
    suspend fun enqueueTask(
        feedId: String,
        feedName: String,
        triggerSource: String
    ): Boolean {
        val taskId = feedId

        // 检查是否正在处理相同的 Feed
        if (currentProcessingFeedId == feedId) {
            Timber.tag(TAG).d("Feed $feedName ($feedId) 正在翻译中，跳过队列添加，触发来源：$triggerSource")
            return false
        }

        // 检查队列中是否已有相同的任务
        if (taskId in processingTasks) {
            Timber.tag(TAG).d("Feed $feedName ($feedId) 已在队列中，跳过队列添加，触发来源：$triggerSource")
            return false
        }

        // 尝试添加到队列
        val success = taskChannel.trySend(
            TitleTranslateTask(
                feedId = feedId,
                feedName = feedName,
                triggerSource = triggerSource
            )
        ).isSuccess

        if (success) {
            processingTasks.add(taskId)
            Timber.tag(TAG).d("Feed $feedName ($feedId) 已添加到翻译队列，触发来源：$triggerSource")
        } else {
            Timber.tag(TAG).w("翻译队列已满，Feed $feedName ($feedId) 无法添加到队列，触发来源：$triggerSource")
        }

        return success
    }

    /**
     * 启动队列处理器
     */
    private fun startQueueProcessor() {
        applicationScope.launch(ioDispatcher) {
            Timber.tag(TAG).d("翻译队列处理器已启动")

            taskChannel.receiveAsFlow().collect { task ->
                processTask(task)
            }
        }
    }

    /**
     * 处理翻译任务
     *
     * @param task 翻译任务
     */
    private suspend fun processTask(task: TitleTranslateTask) {
        val taskId = task.feedId

        Timber.tag(TAG).d("========== 开始处理翻译任务 ==========")
        Timber.tag(TAG).d("Feed ID = ${task.feedId}, Feed 名称 = ${task.feedName}")
        Timber.tag(TAG).d("触发来源 = ${task.triggerSource}")
        Timber.tag(TAG).d("队列剩余任务数 = ${taskChannel.tryReceive().getOrNull()?.let { 1 } ?: 0}")

        try {
            // 标记为正在处理
            currentProcessingFeedId = taskId

            // 通知 TitleTranslateEntry 执行翻译
            // 这里需要通过回调或者事件总线通知
            // 暂时先留空，后续需要集成

            Timber.tag(TAG).d("翻译任务处理完成：${task.feedName} ($taskId)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "翻译任务处理失败：${task.feedName} ($taskId)")
        } finally {
            // 清理状态
            currentProcessingFeedId = null
            processingTasks.remove(taskId)

            Timber.tag(TAG).d("========== 翻译任务处理结束 ==========")
        }
    }

    /**
     * 取消所有待处理的任务
     */
    fun cancelAllPendingTasks() {
        Timber.tag(TAG).d("取消所有待处理的翻译任务")
        // 清空 processingTasks 集合
        processingTasks.clear()
        // 清空 Channel 中的任务
        while (!taskChannel.isEmpty) {
            taskChannel.tryReceive().getOrNull()
        }
    }

    /**
     * 取消当前正在处理的任务
     */
    fun cancelCurrentTask() {
        currentJob?.cancel()
        currentJob = null
        Timber.tag(TAG).d("已取消当前翻译任务")
    }

    /**
     * 取消指定 Feed 的所有翻译任务（包括队列中和正在处理的）
     *
     * @param feedId Feed ID
     */
    fun cancelTaskByFeedId(feedId: String) {
        Timber.tag(TAG).d("取消 Feed $feedId 的所有翻译任务")

        // 如果正在处理该 Feed，取消当前任务
        if (currentProcessingFeedId == feedId) {
            currentJob?.cancel()
            currentJob = null
            Timber.tag(TAG).d("已取消 Feed $feedId 的当前翻译任务")
        }

        // 从队列中移除该 Feed 的任务
        processingTasks.remove(feedId)
        Timber.tag(TAG).d("已从队列中移除 Feed $feedId")
    }

    /**
     * 获取队列状态
     *
     * @return 队列中的任务数量
     */
    fun getQueueSize(): Int {
        return taskChannel.tryReceive().getOrNull()?.let { 1 } ?: 0
    }

    /**
     * 检查是否正在处理指定的 Feed
     *
     * @param feedId Feed ID
     * @return 是否正在处理
     */
    fun isProcessing(feedId: String): Boolean {
        return currentProcessingFeedId == feedId
    }

    /**
     * 检查队列中是否有指定的 Feed
     *
     * @param feedId Feed ID
     * @return 是否在队列中
     */
    fun isInQueue(feedId: String): Boolean {
        return feedId in processingTasks
    }
}
