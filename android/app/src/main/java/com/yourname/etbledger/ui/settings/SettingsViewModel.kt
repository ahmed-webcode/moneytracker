package com.yourname.etbledger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.etbledger.data.local.RuleMatchField
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.CategoryRuleEntity
import com.yourname.etbledger.data.preferences.AppPreferences
import com.yourname.etbledger.data.preferences.UserSettings
import com.yourname.etbledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val accounts: List<AccountEntity> = emptyList(),
    val rules: List<CategoryRuleEntity> = emptyList(),
    val statusMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: LedgerRepository,
    private val preferences: AppPreferences
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.settings,
        repository.observeAccounts(),
        repository.observeCategoryRules(),
        statusMessage
    ) { settings, accounts, rules, msg ->
        SettingsUiState(
            settings = settings,
            accounts = accounts,
            rules = rules,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setAlertsEnabled(enabled: Boolean) {
        preferences.setAlertsEnabled(enabled)
    }

    fun setMonthlyBudgetMinor(limitMinor: Long) {
        preferences.setMonthlyBudgetMinor(limitMinor)
        statusMessage.value = "Monthly outgoing alert threshold updated"
    }

    fun setSyncLookbackDays(days: Int) {
        preferences.setSyncLookbackDays(days)
    }

    fun fullInboxRescan() {
        viewModelScope.launch {
            val result = repository.syncInbox(overrideLookbackDays = 0)
            statusMessage.value = if (result.permissionMissing) {
                "Grant READ_SMS permission first"
            } else {
                "Full scan: ${result.newlyParsedCount} new, ${result.duplicateCount} skipped, ${result.newlyUnparsedCount} to review"
            }
        }
    }

    fun toggleAccountIncluded(accountId: String, included: Boolean) {
        viewModelScope.launch { repository.setAccountIncludedInTotal(accountId, included) }
    }

    fun addRule(
        keyword: String,
        category: String,
        matchField: RuleMatchField,
        typeFilter: TransactionType?,
        priority: Int
    ) {
        viewModelScope.launch {
            repository.addCategoryRule(keyword, category, matchField, typeFilter, priority)
            statusMessage.value = "Rule saved"
        }
    }

    fun toggleRuleEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setCategoryRuleEnabled(ruleId, enabled) }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch { repository.deleteCategoryRule(ruleId) }
    }

    fun reapplyRulesToUncategorized() {
        viewModelScope.launch {
            val count = repository.reapplyRulesToUncategorized()
            statusMessage.value = "Re-categorized $count uncategorized transactions"
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}
