package com.example.paymentapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.paymentapp.data.PaymentIntentException
import com.example.paymentapp.ui.CardSetupScreen
import com.example.paymentapp.ui.PaymentScreen
import com.example.paymentapp.ui.theme.PaymentAppTheme
import com.example.paymentapp.viewmodel.CardSetupViewModel
import com.example.paymentapp.viewmodel.PaymentViewModel
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentelement.CreateIntentWithConfirmationTokenCallback
import com.stripe.android.paymentsheet.CreateIntentResult
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        PaymentConfiguration.init(
            applicationContext,
            getString(R.string.stripe_publishable_key).trim(),
        )

        val paymentViewModel = ViewModelProvider(this)[PaymentViewModel::class.java]
        val paymentSheet = createPaymentSheet(paymentViewModel)

        val sheetConfiguration = PaymentSheet.Configuration.Builder(
            merchantDisplayName = "PaymentApp",
        )
            .billingDetailsCollectionConfiguration(
                PaymentSheet.BillingDetailsCollectionConfiguration(
                    name = PaymentSheet.BillingDetailsCollectionConfiguration.CollectionMode.Always,
                ),
            )
            .build()

        setContent {
            PaymentAppTheme {
                val cardSetupViewModel: CardSetupViewModel = viewModel()
                val cardSetupState by cardSetupViewModel.uiState.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "card_setup",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("card_setup") {
                            CardSetupScreen(
                                uiState = cardSetupState,
                                onCustomerNameChange = cardSetupViewModel::updateCustomerName,
                                onScenarioSelected = cardSetupViewModel::selectScenario,
                                onContinue = {
                                    paymentViewModel.resetForm()
                                    navController.navigate("payment") {
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }

                        composable("payment") {
                            val paymentState by paymentViewModel.uiState.collectAsStateWithLifecycle()
                            val customerName = cardSetupState.customerName.trim()

                            PaymentScreen(
                                customerName = customerName,
                                uiState = paymentState,
                                onAmountChange = paymentViewModel::updateAmount,
                                onCurrencySelected = paymentViewModel::selectCurrency,
                                onNoteChange = paymentViewModel::updateNote,
                                onContinueToCard = {
                                    val checkout = paymentViewModel.prepareCardPayment(customerName)

                                    if (checkout != null) {
                                        paymentSheet.presentWithIntentConfiguration(
                                            intentConfiguration = PaymentSheet.IntentConfiguration(
                                                mode = PaymentSheet.IntentConfiguration.Mode.Payment(
                                                    amount = checkout.amountMinor,
                                                    currency = checkout.currency,
                                                ),
                                            ),
                                            configuration = sheetConfiguration,
                                        )
                                    }
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createPaymentSheet(paymentViewModel: PaymentViewModel): PaymentSheet {
        return PaymentSheet.Builder { result ->
            when (result) {
                is PaymentSheetResult.Completed -> {
                    paymentViewModel.finishCardPayment(
                        success = true,
                        message = "Payment completed.",
                    )
                }

                is PaymentSheetResult.Canceled -> {
                    paymentViewModel.finishCardPayment(success = false, message = null)
                }

                is PaymentSheetResult.Failed -> {
                    val errorTypes = generateSequence(result.error) { it.cause }
                        .joinToString(" -> ") { it.javaClass.simpleName }

                    Log.e("PaymentSheet", "Card checkout failed: $errorTypes")

                    paymentViewModel.finishCardPayment(
                        success = false,
                        message = "Could not complete card checkout.",
                    )
                }
            }
        }
            .createIntentCallback(
                CreateIntentWithConfirmationTokenCallback { token ->
                    try {
                        val clientSecret = paymentViewModel.createCardPaymentIntent(token.id)
                        CreateIntentResult.Success(clientSecret)
                    } catch (error: PaymentIntentException) {
                        CreateIntentResult.Failure(
                            error,
                            error.message ?: "Could not prepare the payment.",
                        )
                    }
                },
            )
            .build(this)
    }
}