package com.example.retrofitclient.model

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    @SerializedName("id_token")
    val idToken: String
)