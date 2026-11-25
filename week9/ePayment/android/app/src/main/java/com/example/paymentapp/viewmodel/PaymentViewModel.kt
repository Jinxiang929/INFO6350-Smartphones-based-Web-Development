package com.example.paymentapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.paymentapp.data.PaymentIntentException
import com.example.paymentapp.data.PaymentRepository
import com.example.paymentapp.model.CardCheckoutModel
import com.example.paymentapp.model.PaymentIntentRequestModel
import com.example.paymentapp.model.PaymentModel
import com.example.paymentapp.model.PaymentRequestModel
import com.example.paymentapp.model.PaymentResponseModel
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.UUID

class PaymentViewModel : ViewModel() {
    private val repository = PaymentRepository()
    private val _uiState = MutableStateFlow(PaymentModel())
    val uiState = _uiState.asStateFlow()

    private val amountPattern = Regex("""[0-9]+([.,][0-9]{1,2})?""")
    private var activeCheckout: CardCheckoutModel? = null
    private var activeIntentId: String? = null

    fun resetForm() {
        activeCheckout = null
        activeIntentId = null
        _uiState.value = PaymentModel()
    }

    fun updateAmount(amount: String) {
        _uiState.update { state ->
            state.copy(amount = amount, amountError = null, result = null)
        }
    }

    fun selectCurrency(currency: String) {
        _uiState.update { state ->
            state.copy(currency = currency, result = null)
        }
    }

    fun updateNote(note: String) {
        _uiState.update { state ->
            state.copy(note = note, result = null)
        }
    }

    fun prepareCardPayment(customerName: String): CardCheckoutModel? {
        val state = _uiState.value
        if (state.isSubmitting) return null

        val amountMinor = validateAmount(state.amount) ?: return null
        val checkout = CardCheckoutModel(
            amountMinor = amountMinor,
            currency = state.currency,
            customerName = customerName.trim(),
            note = state.note.trim(),
            checkoutId = UUID.randomUUID().toString(),
        )

        activeCheckout = checkout
        activeIntentId = null
        _uiState.update {
            it.copy(isSubmitting = true, amountError = null, result = null)
        }

        return checkout
    }

    suspend fun createCardPaymentIntent(confirmationTokenId: String): String =
        withContext(Dispatchers.Main.immediate) {
            val checkout = activeCheckout
                ?: throw PaymentIntentException("Reopen the card form and try again.")

            val request = PaymentIntentRequestModel(
                amount = checkout.amountMinor,
                currency = checkout.currency,
                customerName = checkout.customerName,
                note = checkout.note,
                confirmationTokenId = confirmationTokenId,
                checkoutId = checkout.checkoutId,
            )

            val intent = try {
                repository.createPaymentIntent(request)
            } catch (error: SocketTimeoutException) {
                throw PaymentIntentException("Flask took too long to respond.", error)
            } catch (error: IOException) {
                throw PaymentIntentException("Could not reach Flask. Check that it is running.", error)
            } catch (error: JsonParseException) {
                throw PaymentIntentException("Could not read the Flask response.", error)
            }

            val intentId = intent.paymentIntentId?.takeIf { it.isNotBlank() }
                ?: throw PaymentIntentException("Flask did not return a payment ID.")
            val clientSecret = intent.clientSecret?.takeIf { it.isNotBlank() }
                ?: throw PaymentIntentException("Flask did not return a client secret.")

            if (activeCheckout !== checkout) {
                throw PaymentIntentException("Reopen the card form and try again.")
            }

            activeIntentId = intentId
            clientSecret
        }

    fun finishCardPayment(success: Boolean, message: String?) {
        if (activeCheckout == null) return

        val intentId = activeIntentId
        activeCheckout = null
        activeIntentId = null

        _uiState.update {
            it.copy(
                isSubmitting = false,
                result = message?.let { text ->
                    PaymentResponseModel(
                        success = success,
                        message = text,
                        paymentIntentId = intentId,
                    )
                },
            )
        }
    }

    fun submitPayment(customerName: String, testScenario: String) {
        val state = _uiState.value
        if (state.isSubmitting) return

        val amountMinor = validateAmount(state.amount) ?: return
        val request = PaymentRequestModel(
            amount = amountMinor,
            currency = state.currency,
            note = state.note.trim(),
            customerName = customerName.trim(),
            testScenario = testScenario,
        )

        _uiState.update {
            it.copy(isSubmitting = true, amountError = null, result = null)
        }

        viewModelScope.launch {
            val result = try {
                repository.createPayment(request)
            } catch (_: SocketTimeoutException) {
                PaymentResponseModel(
                    message = "The request timed out. Check the payment before retrying.",
                )
            } catch (_: IOException) {
                PaymentResponseModel(
                    message = "Network error. Check Flask and verify the payment before retrying.",
                )
            } catch (_: JsonParseException) {
                PaymentResponseModel(
                    message = "Could not read the server response. Check the payment before retrying.",
                )
            }

            _uiState.update {
                it.copy(isSubmitting = false, result = result)
            }
        }
    }

    private fun validateAmount(amount: String): Long? {
        val input = amount.trim()

        if (input.isEmpty()) {
            showAmountError("Enter an amount.")
            return null
        }

        if (!amountPattern.matches(input)) {
            showAmountError("Enter a positive amount with up to two decimal places.")
            return null
        }

        val amountMinor = try {
            input.replace(',', '.').toBigDecimal().movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            showAmountError("Amount is too large.")
            return null
        }

        if (amountMinor <= 0) {
            showAmountError("Amount must be greater than zero.")
            return null
        }

        return amountMinor
    }

    private fun showAmountError(message: String) {
        _uiState.update {
            it.copy(amountError = message, result = null)
        }
    }
}