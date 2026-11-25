package com.example.paymentapp.model

import com.google.gson.annotations.SerializedName

data class PaymentIntentResponseModel(
    @SerializedName("payment_intent_id")
    val paymentIntentId: String?,
    @SerializedName("client_secret")
    val clientSecret: String?,
)