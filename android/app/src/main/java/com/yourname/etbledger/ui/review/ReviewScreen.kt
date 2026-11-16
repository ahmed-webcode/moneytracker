package com.yourname.etbledger.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.UnparsedSmsEntity
import com.yourname.etbledger.data.parser.MoneyUtils
import com.yourname.etbledger.ui.common.StandardCategories
import com.yourname.etbledger.ui.common.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val pending by viewModel.pendingSms.collectAsStateWithLifecycle()
    var convertingSms by remember { mutableStateOf<UnparsedSmsEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unparsed SMS Review", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        if (pending.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CheckCircleOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "All caught up!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Unrecognized bank/wallet SMS messages will appear here for manual inspection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pending, key = { it.id }) { item ->
                    UnparsedSmsCard(
                        item = item,
                        onConvert = { convertingSms = item },
                        onDismiss = { viewModel.dismiss(item.id) }
                    )
                }
            }
        }
    }

    convertingSms?.let { sms ->
        ConvertUnparsedDialog(
            sms = sms,
            onDismiss = { convertingSms = null },
            onSave = { accountName, type, amountMinor, feeMinor, category, counterparty, selfTransfer, note ->
                viewModel.convertToTransaction(
                    unparsedId = sms.id,
                    accountName = accountName,
                    type = type,
                    amountMinor = amountMinor,
                    feeMinor = feeMinor,
                    category = category,
                    counterparty = counterparty,
                    isSelfTransfer = selfTransfer,
                    note = note
                )
                convertingSms = null
            }
        )
    }
}

@Composable
private fun UnparsedSmsCard(
    item: UnparsedSmsEntity,
    onConvert: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDateTime(item.receivedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item.failureReason?.let { reason ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Dismiss")
                }
                Button(
                    onClick = onConvert,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Convert")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvertUnparsedDialog(
    sms: UnparsedSmsEntity,
    onDismiss: () -> Unit,
    onSave: (
        accountName: String,
        type: TransactionType,
        amountMinor: Long,
        feeMinor: Long,
        category: String,
        counterparty: String,
        isSelfTransfer: Boolean,
        note: String
    ) -> Unit
) {
    val guessedAmount = remember(sms.body) {
        Regex("""(?:ETB|Birr)\s*([\d,]+\.\d{2}|[\d,]+)""", RegexOption.IGNORE_CASE)
            .find(sms.body)?.groupValues?.getOrNull(1)?.replace(",", "") ?: ""
    }
    val guessedType = remember(sms.body) {
        if (sms.body.contains("received", ignoreCase = true) ||
            sms.body.contains("credited", ignoreCase = true)
        ) {
            TransactionType.INCOME
        } else {
            TransactionType.OUTGOING
        }
    }

    var accountName by remember { mutableStateOf(sms.sender) }
    var type by remember { mutableStateOf(guessedType) }
    var amountText by remember { mutableStateOf(guessedAmount) }
    var feeText by remember { mutableStateOf("0.00") }
    var counterparty by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Uncategorized") }
    var isSelfTransfer by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert SMS to Transaction") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = type == TransactionType.OUTGOING,
                        onClick = { type = TransactionType.OUTGOING },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text("Outgoing") }
                    SegmentedButton(
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text("Income") }
                }

                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    label = { Text("Bank / Wallet Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMsg = null
                    },
                    label = { Text("Amount (ETB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it },
                    label = { Text("Fee (ETB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Counterparty / Merchant") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StandardCategories.take(8).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Self-Transfer", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isSelfTransfer, onCheckedChange = { isSelfTransfer = it })
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = MoneyUtils.tryParseEtbToMinor(amountText)
                if (amt == null || amt <= 0L) {
                    errorMsg = "Enter a valid ETB amount"
                    return@Button
                }
                val fee = MoneyUtils.tryParseEtbToMinor(feeText) ?: 0L
                onSave(accountName, type, amt, fee, category, counterparty, isSelfTransfer, note)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
