package me.ash.reader.ui.ext

import android.annotation.SuppressLint
import android.content.Context
import me.ash.reader.R
import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@SuppressLint("SimpleDateFormat")
object DateFormat {
    val YYYY_MM_DD_HH_MM_SS = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val YYYY_MM_DD_DASH_HH_MM_SS = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss")
    val YYYY_MM_DD_DASH_HH_MM_SS_DASH = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss")
}

fun Date.toString(format: SimpleDateFormat): String {
    return format.format(this)
}

fun Date.formatAsString(
    context: Context,
    onlyHourMinute: Boolean? = false,
    atHourMinute: Boolean? = false,
): String {
    val locale = Locale.getDefault()
    val df = DateFormat.getDateInstance(DateFormat.FULL, locale)
    return when {
        onlyHourMinute == true -> {
            this.toTimeString(context = context)
        }

        atHourMinute == true -> {
            context.getString(
                R.string.date_at_time,
                df.format(this),
                this.toTimeString(context = context),
            )
        }

        else -> {
            df.format(this).run {
                when (this) {
                    df.format(Date()) -> context.getString(R.string.today)
                    df.format(
                        Calendar.getInstance().apply {
                            time = Date()
                            add(Calendar.DAY_OF_MONTH, -1)
                        }.time
                    ),
                    -> context.getString(R.string.yesterday)

                    else -> this
                }
            }
        }
    }
}

private fun Date.toTimeString(context: Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(this)

fun Date.formatAsRelativeTime(): String {
    val now = Date()
    val diff = now.time - this.time
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}m 前"
        hours < 24 -> "${hours}h 前"
        else -> "${days}天前"
    }
}


private fun String.parseToDate(
    patterns: Array<String> = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd",
        "yyyy-MM-dd HH:mm:ss",
        "yyyyMMdd",
        "yyyy/MM/dd",
        "yyyy年MM月dd日",
        "yyyy MM dd",
    ),
): Date? {
    val df = SimpleDateFormat()
    for (pattern in patterns) {
        df.applyPattern(pattern)
        df.isLenient = false
        val date = df.parse(this, ParsePosition(0))
        if (date != null) {
            return date
        }
    }
    return null
}

fun Date.isFuture(staticDate: Date = Date()): Boolean = this.time > staticDate.time
