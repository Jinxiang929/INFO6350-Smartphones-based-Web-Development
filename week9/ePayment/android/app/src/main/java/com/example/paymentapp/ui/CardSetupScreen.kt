package com.example.paymentapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.paymentapp.model.CardSetupModel

@Composable
fun CardSetupScreen(
    uiState: CardSetupModel,
    onCustomerNameChange: (String) -> Unit,
    onScenarioSelected: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val scenarios = listOf(
        "success" to "Successful payment",
        "decline" to "Card declined",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Card Setup",
            style = MaterialTheme.typography.headlineMedium,
        )

        OutlinedTextField(
            value = uiState.customerName,
            onValueChange = onCustomerNameChange,
            label = { Text("Customer name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Test scenario")

        Column(modifier = Modifier.selectableGroup()) {
            scenarios.forEach { (value, label) ->
                val selected = uiState.testScenario == value

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected,
                            onClick = { onScenarioSelected(value) },
                            role = Role.RadioButton,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Text(
                        text = label,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onContinue()
            },
            enabled = uiState.customerName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}