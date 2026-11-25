package com.example.paymentapp.model

import com.google.gson.annotations.SerializedName

data class PaymentIntentRequestModel(
    val amount: Long,
    val currency: String,
    @SerializedName("customer_name")
    val customerName: String,
    val note: String,
    @SerializedName("confirmation_token_id")
    val confirmationTokenId: String,
    @SerializedName("checkout_id")
    val checkoutId: String,
)