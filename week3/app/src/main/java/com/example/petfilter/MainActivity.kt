package com.example.petfilter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.petfilter.ui.theme.PetFilterTheme

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.rememberAsyncImagePainter

import androidx.compose.runtime.rememberCoroutineScope
import com.example.petfilter.network.RetrofitClient
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            PetFilterTheme {
                PetFilterScreen()
            }
        }
    }
}

@Composable
fun PetFilterScreen() {

    var selectedFilter by remember {
        mutableStateOf("Cartoon")
    }

    var selectedImageUri by remember {
        mutableStateOf<Uri?>(null)
    }

    var backendMessage by remember {
        mutableStateOf("")
    }

    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            selectedImageUri = uri
        }

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "🐾 PetFilter",
                style = MaterialTheme.typography.headlineLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Transform your pet with AI filters"
            )

            Spacer(modifier = Modifier.height(40.dp))

            if (selectedImageUri != null) {

                Image(
                    painter = rememberAsyncImagePainter(selectedImageUri),
                    contentDescription = "Selected pet photo",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                }
            ) {
                Text("Choose Photo")
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Choose a style",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                FilterButton(
                    name = "Cartoon",
                    selected = selectedFilter == "Cartoon"
                ) {
                    selectedFilter = "Cartoon"
                }

                FilterButton(
                    name = "Anime",
                    selected = selectedFilter == "Anime"
                ) {
                    selectedFilter = "Anime"
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                FilterButton(
                    name = "Painting",
                    selected = selectedFilter == "Painting"
                ) {
                    selectedFilter = "Painting"
                }

                FilterButton(
                    name = "3D",
                    selected = selectedFilter == "3D"
                ) {
                    selectedFilter = "3D"
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {

                    coroutineScope.launch {

                        try {

                            backendMessage = "Connecting..."

                            val response =
                                RetrofitClient.apiService.generate()

                            backendMessage = response.message

                        } catch (e: Exception) {

                            backendMessage =
                                "Error: ${e.message}"
                        }
                    }
                }
            ) {
                Text("Generate ✨")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = backendMessage)
        }
    }
}

@Composable
fun FilterButton(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    if (selected) {
        Button(onClick = onClick) {
            Text(name)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(name)
        }
    }
}