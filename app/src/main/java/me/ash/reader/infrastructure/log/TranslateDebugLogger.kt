package me.ash.reader.infrastructure.log

import android.content.Context
import android.util.Log
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.ash.reader.infrastructure.di.ApplicationScope
import me.ash.reader.infrastructure.di.IODispatcher
import me.ash.reader.infrastructure.preference.SettingsProvider

/**
 * 专门记录翻译相关流程的日志（trans.log），与图片下载日志分离。
 *
 * 写入策略与 [ImageDownloadDebugLogger] 一致：
 * - 受 Settings.translateDebugLog 开关控制
 * - 写到 filesDir/logs/trans.log
 * - 单文件超过 4MB 时直接清空（轮转，简单粗暴，保证不爆）
 * - Logcat 同步打印（tag=TranslateDebug），方便实时联调
 */
@Singleton
class TranslateDebugLogger
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val settingsProvider: SettingsProvider,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val lock = Any()
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logFile: File
        get() = context.filesDir.resolve("logs").resolve(LOG_FILE_NAME)

    fun log(tag: String, message: () -> String) {
        if (!settingsProvider.settings.translateDebugLog.value) return

        val rendered = try {
            message()
        } catch (t: Throwable) {
            "<log render failed: ${t.javaClass.simpleName}:${t.message}>"
        }
        val line = "${formatter.format(Date())} [$tag] $rendered"
        Log.d(LOGCAT_TAG, line)
        applicationScope.launch(ioDispatcher) {
            if (!settingsProvider.settings.translateDebugLog.value) return@launch
            synchronized(lock) {
                val file = logFile
                file.parentFile?.mkdirs()
                rotateIfNeeded(file)
                file.appendText("$line\n")
            }
        }
    }

    /** 便利方法：直接给消息，没有特别 tag 时用 "Trans"。 */
    fun log(message: () -> String) = log(DEFAULT_TAG, message)

    /** 记录异常。stack trace 写入 log 文件，方便事后定位。 */
    fun error(tag: String, throwable: Throwable, message: () -> String) {
        if (!settingsProvider.settings.translateDebugLog.value) return
        val rendered = try {
            message()
        } catch (t: Throwable) {
            "<log render failed: ${t.javaClass.simpleName}:${t.message}>"
        }
        val stack = throwable.stackTraceToString()
        val line =
            "${formatter.format(Date())} [$tag] ERROR $rendered :: " +
                "${throwable.javaClass.simpleName}: ${throwable.message}\n$stack"
        Log.e(LOGCAT_TAG, line, throwable)
        applicationScope.launch(ioDispatcher) {
            if (!settingsProvider.settings.translateDebugLog.value) return@launch
            synchronized(lock) {
                val file = logFile
                file.parentFile?.mkdirs()
                rotateIfNeeded(file)
                file.appendText("$line\n")
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (file.exists() && file.length() > MAX_LOG_BYTES) {
            file.writeText("")
        }
    }

    companion object {
        private const val LOGCAT_TAG = "TranslateDebug"
        private const val DEFAULT_TAG = "Trans"
        private const val LOG_FILE_NAME = "trans.log"
        private const val MAX_LOG_BYTES = 4L * 1024L * 1024L

        fun from(context: Context): TranslateDebugLogger =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                TranslateDebugLoggerEntryPoint::class.java,
            ).translateDebugLogger()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranslateDebugLoggerEntryPoint {
    fun translateDebugLogger(): TranslateDebugLogger
}
