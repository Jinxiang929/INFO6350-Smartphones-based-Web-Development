package com.example.paymentapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.paymentapp.model.PaymentModel

@Composable
fun PaymentScreen(
    customerName: String,
    uiState: PaymentModel,
    onAmountChange: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onContinueToCard: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val formEnabled = !uiState.isSubmitting

    BackHandler(enabled = uiState.isSubmitting) {}

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Payment",
            style = MaterialTheme.typography.headlineMedium,
        )

        Text("Customer: $customerName")

        OutlinedTextField(
            value = uiState.amount,
            onValueChange = onAmountChange,
            enabled = formEnabled,
            label = { Text("Amount") },
            placeholder = { Text("25.00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = uiState.amountError != null,
            supportingText = {
                uiState.amountError?.let { Text(it) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Column {
            Text("Currency")

            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val currencies = listOf("usd" to "USD", "cad" to "CAD")

                currencies.forEach { (value, label) ->
                    val selected = uiState.currency == value

                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = selected,
                                enabled = formEnabled,
                                onClick = { onCurrencySelected(value) },
                                role = Role.RadioButton,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            enabled = formEnabled,
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        OutlinedTextField(
            value = uiState.note,
            onValueChange = onNoteChange,
            enabled = formEnabled,
            label = { Text("Note") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                focusManager.clearFocus()
                onContinueToCard()
            },
            enabled = formEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue to card")
        }

        uiState.result?.let { result ->
            val message = result.message
                ?: if (result.success) "Payment completed." else "Payment did not complete."
            Text(
                text = message,
                color = if (result.success) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            result.status?.let { Text("Status: $it") }
            result.paymentIntentId?.let { Text("Payment ID: $it") }

            when (result.logged) {
                true -> Text("Logged to Google Sheets.")
                false -> Text("Google Sheets write unconfirmed.")
                null -> Unit
            }
        }

        OutlinedButton(onClick = onBack, enabled = formEnabled) {
            Text("Back")
        }
    }
}