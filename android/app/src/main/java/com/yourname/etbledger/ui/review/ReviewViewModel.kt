package com.yourname.etbledger.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.UnparsedSmsEntity
import com.yourname.etbledger.data.repository.LedgerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: LedgerRepository
) : ViewModel() {

    val pendingSms: StateFlow<List<UnparsedSmsEntity>> = repository.observePendingUnparsed()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun convertToTransaction(
        unparsedId: Long,
        accountName: String,
        type: TransactionType,
        amountMinor: Long,
        feeMinor: Long,
        category: String,
        counterparty: String,
        isSelfTransfer: Boolean,
        note: String
    ) {
        viewModelScope.launch {
            repository.convertUnparsedToTransaction(
                unparsedId = unparsedId,
                accountName = accountName,
                type = type,
                amountMinor = amountMinor,
                feeMinor = feeMinor,
                category = category,
                counterparty = counterparty,
                isSelfTransfer = isSelfTransfer,
                note = note
            )
        }
    }

    fun dismiss(unparsedId: Long) {
        viewModelScope.launch {
            repository.dismissUnparsedSms(unparsedId)
        }
    }
}
