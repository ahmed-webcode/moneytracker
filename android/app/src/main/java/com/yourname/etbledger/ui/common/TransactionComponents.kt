package com.yourname.etbledger.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yourname.etbledger.data.local.TransactionSource
import com.yourname.etbledger.data.local.TransactionType
import com.yourname.etbledger.data.local.entity.AccountEntity
import com.yourname.etbledger.data.local.entity.TransactionEntity
import com.yourname.etbledger.data.parser.MoneyUtils
import com.yourname.etbledger.ui.theme.IncomeGreen
import com.yourname.etbledger.ui.theme.IncomeGreenBg
import com.yourname.etbledger.ui.theme.OutgoingCoral
import com.yourname.etbledger.ui.theme.OutgoingCoralBg
import com.yourname.etbledger.ui.theme.TransferAmber
import com.yourname.etbledger.ui.theme.TransferAmberBg

@Composable
fun TransactionRowCard(
    transaction: TransactionEntity,
    accountDisplayName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val icon = when {
        transaction.isSelfTransfer -> Icons.Default.SwapHoriz
        transaction.type == TransactionType.INCOME -> Icons.Default.ArrowDownward
        else -> Icons.Default.ArrowUpward
    }
    val tint = when {
        transaction.isSelfTransfer -> TransferAmber
        transaction.type == TransactionType.INCOME -> IncomeGreen
        else -> OutgoingCoral
    }
    val bgTint = when {
        transaction.isSelfTransfer -> TransferAmberBg
        transaction.type == TransactionType.INCOME -> IncomeGreenBg
        else -> OutgoingCoralBg
    }

    val displayAmount = if (transaction.type == TransactionType.OUTGOING) {
        transaction.totalAmountMinor
    } else {
        transaction.amountMinor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = bgTint,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.counterparty?.takeIf { it.isNotBlank() } ?: transaction.category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(accountDisplayName)
                        append(" • ")
                        append(transaction.category)
                        if (transaction.isSelfTransfer) append(" • Self-Transfer")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatDateTime(transaction.timestampMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatEtb(displayAmount, signedForType = transaction.type),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
                if (transaction.feeMinor > 0L) {
                    Text(
                        text = "Fee ${formatEtb(transaction.feeMinor)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailBottomSheet(
    transaction: TransactionEntity,
    account: AccountEntity?,
    onDismiss: () -> Unit,
    onToggleSelfTransfer: (Boolean) -> Unit,
    onSaveCategoryAndNote: (String, String?) -> Unit,
    onDelete: () -> Unit
) {
    var selectedCategory by remember(transaction.id) { mutableStateOf(transaction.category) }
    var noteText by remember(transaction.id) { mutableStateOf(transaction.note.orEmpty()) }
    var isSelfTransfer by remember(transaction.id) { mutableStateOf(transaction.isSelfTransfer) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = transaction.counterparty ?: "Transaction Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${account?.displayName ?: transaction.accountId} • ${formatDateTime(transaction.timestampMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = formatEtb(
                    if (transaction.type == TransactionType.OUTGOING) transaction.totalAmountMinor else transaction.amountMinor,
                    signedForType = transaction.type
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == TransactionType.INCOME) IncomeGreen else OutgoingCoral
            )

            if (transaction.feeMinor > 0L) {
                Text(
                    text = "Principal: ${formatEtb(transaction.amountMinor)} + Fee: ${formatEtb(transaction.feeMinor)} " +
                        "(Service ${formatEtb(transaction.serviceFeeMinor)}, VAT ${formatEtb(transaction.vatMinor)}" +
                        if (transaction.extraFeeMinor > 0L) ", DR ${formatEtb(transaction.extraFeeMinor)})" else ")",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            transaction.balanceAfterMinor?.let { bal ->
                Text(
                    text = "Remaining Balance: ${formatEtb(bal)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mark as Self-Transfer", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Excludes internal moves (e.g. CBE → telebirr) from Income & Outgoing totals",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSelfTransfer,
                    onCheckedChange = {
                        isSelfTransfer = it
                        onToggleSelfTransfer(it)
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StandardCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            transaction.referenceId?.let { ref ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Ref ID: $ref",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            transaction.rawSmsBody?.let { sms ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = sms,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (transaction.source != TransactionSource.SMS) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = OutgoingCoral)
                    ) {
                        Text("Delete")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                Button(onClick = {
                    onSaveCategoryAndNote(selectedCategory, noteText)
                    onDismiss()
                }) {
                    Text("Save Changes")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCashTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: TransactionType, amountMinor: Long, category: String, counterparty: String, note: String) -> Unit
) {
    var type by remember { mutableStateOf(TransactionType.OUTGOING) }
    var amountText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food & Groceries") }
    var counterparty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Cash Transaction") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorMsg = null
                    },
                    label = { Text("Amount (ETB)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = errorMsg != null,
                    supportingText = errorMsg?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = counterparty,
                    onValueChange = { counterparty = it },
                    label = { Text("Merchant / Person (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StandardCategories.take(8).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedMinor = MoneyUtils.tryParseEtbToMinor(amountText)
                if (parsedMinor == null || parsedMinor <= 0L) {
                    errorMsg = "Enter a valid positive ETB amount"
                    return@Button
                }
                onConfirm(type, parsedMinor, category, counterparty, note)
                onDismiss()
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
