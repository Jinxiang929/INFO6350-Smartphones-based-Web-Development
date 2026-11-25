package com.example.paymentapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.paymentapp.data.PaymentRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaymentRepositoryTest {
    @Test
    fun healthReturnsOk() {
        runBlocking {
            val health = PaymentRepository().checkHealth()
            assertEquals("ok", health.status)
        }
    }
}