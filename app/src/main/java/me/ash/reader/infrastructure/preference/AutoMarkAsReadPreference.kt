package me.ash.reader.infrastructure.preference

import android.content.Context
import me.ash.reader.R
import me.ash.reader.ui.page.settings.accounts.AccountViewModel

sealed class AutoMarkAsReadPreference(
    val value: Long,
) {

    object For1Day : AutoMarkAsReadPreference(86400000L)
    object For2Days : AutoMarkAsReadPreference(172800000L)
    object For3Days : AutoMarkAsReadPreference(259200000L)
    object For4Days : AutoMarkAsReadPreference(345600000L)
    object For5Days : AutoMarkAsReadPreference(432000000L)

    fun put(accountId: Int, viewModel: AccountViewModel) {
        viewModel.update(accountId) { copy(autoMarkAsRead = this@AutoMarkAsReadPreference) }
    }

    fun toDesc(context: Context): String =
        when (this) {
            For1Day -> context.getString(R.string.for_1_day)
            For2Days -> context.getString(R.string.for_2_days)
            For3Days -> context.getString(R.string.for_3_days)
            For4Days -> context.getString(R.string.for_4_days)
            For5Days -> context.getString(R.string.for_5_days)
        }

    companion object {

        val default = For1Day
        val values = listOf(
            For1Day,
            For2Days,
            For3Days,
            For4Days,
            For5Days,
        )
    }
}
