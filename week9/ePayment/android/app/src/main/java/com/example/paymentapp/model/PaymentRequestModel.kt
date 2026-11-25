package com.example.paymentapp.model

import com.google.gson.annotations.SerializedName

data class PaymentRequestModel(
    val amount: Long,
    val currency: String,
    val note: String,
    @SerializedName("customer_name") val customerName: String,
    @SerializedName("test_scenario") val testScenario: String,
)