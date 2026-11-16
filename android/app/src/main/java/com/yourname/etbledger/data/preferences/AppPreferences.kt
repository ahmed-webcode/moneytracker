package com.yourname.etbledger.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UserSettings(
    val alertsEnabled: Boolean = true,
    val monthlyOutgoingBudgetMinor: Long = 0L,
    val syncLookbackDays: Int = 90,
    val lastSyncMillis: Long = 0L
)

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("etb_ledger_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    private fun readSettings(): UserSettings = UserSettings(
        alertsEnabled = prefs.getBoolean(KEY_ALERTS_ENABLED, true),
        monthlyOutgoingBudgetMinor = prefs.getLong(KEY_MONTHLY_BUDGET_MINOR, 0L),
        syncLookbackDays = prefs.getInt(KEY_SYNC_LOOKBACK_DAYS, 90),
        lastSyncMillis = prefs.getLong(KEY_LAST_SYNC_MILLIS, 0L)
    )

    fun setAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALERTS_ENABLED, enabled).apply()
        _settings.value = readSettings()
    }

    fun setMonthlyBudgetMinor(limitMinor: Long) {
        prefs.edit().putLong(KEY_MONTHLY_BUDGET_MINOR, limitMinor.coerceAtLeast(0L)).apply()
        _settings.value = readSettings()
    }

    fun setSyncLookbackDays(days: Int) {
        prefs.edit().putInt(KEY_SYNC_LOOKBACK_DAYS, days.coerceIn(7, 3650)).apply()
        _settings.value = readSettings()
    }

    fun setLastSyncMillis(timestampMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_MILLIS, timestampMillis).apply()
        _settings.value = readSettings()
    }

    companion object {
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_MONTHLY_BUDGET_MINOR = "monthly_budget_minor"
        private const val KEY_SYNC_LOOKBACK_DAYS = "sync_lookback_days"
        private const val KEY_LAST_SYNC_MILLIS = "last_sync_millis"
    }
}
