package com.example.paymentapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.paymentapp.model.CardSetupModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CardSetupViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(CardSetupModel())
    val uiState = _uiState.asStateFlow()

    fun updateCustomerName(name: String) {
        _uiState.update { state -> state.copy(customerName = name) }
    }

    fun selectScenario(scenario: String) {
        _uiState.update { state -> state.copy(testScenario = scenario) }
    }
}