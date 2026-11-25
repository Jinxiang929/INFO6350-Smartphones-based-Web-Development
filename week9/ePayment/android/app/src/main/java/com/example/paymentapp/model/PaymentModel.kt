package com.example.paymentapp.model

data class PaymentModel(
    val amount: String = "",
    val currency: String = "usd",
    val note: String = "",
    val amountError: String? = null,
    val isSubmitting: Boolean = false,
    val result: PaymentResponseModel? = null,
)