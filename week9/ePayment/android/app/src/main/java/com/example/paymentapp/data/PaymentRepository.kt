package com.example.paymentapp.data

import com.example.paymentapp.model.HealthModel
import com.example.paymentapp.model.PaymentIntentRequestModel
import com.example.paymentapp.model.PaymentIntentResponseModel
import com.example.paymentapp.model.PaymentRequestModel
import com.example.paymentapp.model.PaymentResponseModel
import com.google.gson.Gson
import com.google.gson.JsonParseException
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

internal interface PaymentApi {
    @GET("health")
    suspend fun getHealth(): HealthModel

    @POST("payments/create")
    suspend fun createPayment(
        @Body request: PaymentRequestModel,
    ): Response<PaymentResponseModel>

    @POST("payments/intents")
    suspend fun createPaymentIntent(
        @Body request: PaymentIntentRequestModel,
    ): Response<PaymentIntentResponseModel>
}

class PaymentIntentException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PaymentRepository {
    private val gson = Gson()

    private val httpClient = OkHttpClient.Builder()
        .readTimeout(40, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:5050/")
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(PaymentApi::class.java)

    suspend fun checkHealth(): HealthModel = api.getHealth()

    suspend fun createPayment(request: PaymentRequestModel): PaymentResponseModel {
        val response = api.createPayment(request)

        if (response.isSuccessful) {
            return response.body() ?: PaymentResponseModel(
                message = "The server returned an empty response. Check the payment before retrying.",
            )
        }

        return readError(response)
    }

    suspend fun createPaymentIntent(
        request: PaymentIntentRequestModel,
    ): PaymentIntentResponseModel {
        val response = api.createPaymentIntent(request)

        if (!response.isSuccessful) {
            throw PaymentIntentException(
                readError(response).message ?: "Could not prepare the payment.",
            )
        }

        return response.body()
            ?: throw PaymentIntentException("The server returned an empty response.")
    }

    private fun readError(response: Response<*>): PaymentResponseModel {
        val error = try {
            response.errorBody()?.use { body ->
                gson.fromJson(body.charStream(), PaymentResponseModel::class.java)
            }
        } catch (_: JsonParseException) {
            null
        }

        val message = error?.message?.takeIf { it.isNotBlank() }
            ?: "The server returned HTTP ${response.code()}."

        return error?.copy(success = false, message = message)
            ?: PaymentResponseModel(message = message)
    }
}