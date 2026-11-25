package com.example.paymentapp.model

data class CardCheckoutModel(
    val amountMinor: Long,
    val currency: String,
    val customerName: String,
    val note: String,
    val checkoutId: String,
)