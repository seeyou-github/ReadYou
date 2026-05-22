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

@Singleton
class ImageDownloadDebugLogger
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

    fun log(message: () -> String) {
        if (!settingsProvider.settings.imageDownloadDebugLog.value) return

        write(message)
    }

    fun logAlways(message: () -> String) {
        write(message)
    }

    private fun write(message: () -> String) {
        val line = "${formatter.format(Date())} ${message()}"
        Log.d(TAG, line)
        applicationScope.launch(ioDispatcher) {
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
        private const val TAG = "ImageDownload"
        private const val LOG_FILE_NAME = "image_download.log"
        private const val MAX_LOG_BYTES = 2L * 1024L * 1024L

        fun from(context: Context): ImageDownloadDebugLogger =
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                ImageDownloadDebugLoggerEntryPoint::class.java,
            ).imageDownloadDebugLogger()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ImageDownloadDebugLoggerEntryPoint {
    fun imageDownloadDebugLogger(): ImageDownloadDebugLogger
}
