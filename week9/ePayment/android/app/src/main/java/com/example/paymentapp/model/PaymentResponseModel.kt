package com.example.paymentapp.model

import com.google.gson.annotations.SerializedName

data class PaymentResponseModel(
    val success: Boolean = false,
    val status: String? = null,
    val message: String? = null,
    @SerializedName("payment_intent_id") val paymentIntentId: String? = null,
    @SerializedName("error_code") val errorCode: String? = null,
    val logged: Boolean? = null,
)