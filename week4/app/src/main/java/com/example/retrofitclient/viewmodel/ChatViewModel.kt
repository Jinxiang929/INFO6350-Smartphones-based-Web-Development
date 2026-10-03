package com.example.retrofitclient.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.retrofitclient.repository.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.util.Log

class ChatViewModel : ViewModel() {

    private val repository = ChatRepository()

    private val _responseText = MutableStateFlow("")
    val responseText: StateFlow<String> = _responseText

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun sendMessage(prompt: String) {

        if (prompt.isBlank()) {
            _responseText.value = "Please enter a message."
            return
        }

        viewModelScope.launch {

            _isLoading.value = true

            try {
                val result = repository.sendMessage(prompt)
                _responseText.value = result.response
            } catch (e: Exception) {
                Log.e("RetrofitError", "Request failed", e)
                _responseText.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}