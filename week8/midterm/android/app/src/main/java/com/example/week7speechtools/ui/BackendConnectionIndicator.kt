package com.example.week7speechtools.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.week7speechtools.viewmodel.BackendConnectionStatus
import com.example.week7speechtools.viewmodel.HealthViewModel

@Composable
fun BackendConnectionIndicator(
    model: HealthViewModel = viewModel(factory = HealthViewModel.Factory)
) {
    val status by model.connectionStatus.collectAsStateWithLifecycle()

    val label = when (status) {
        BackendConnectionStatus.CHECKING -> "Checking..."
        BackendConnectionStatus.CONNECTED -> "Connected"
        BackendConnectionStatus.NOT_CONNECTED -> "Not connected"
    }

    val dotColor = when (status) {
        BackendConnectionStatus.CHECKING -> Color(0xFF757575)
        BackendConnectionStatus.CONNECTED -> Color(0xFF43A047)
        BackendConnectionStatus.NOT_CONNECTED -> Color(0xFFE53935)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(
            space = 8.dp,
            alignment = Alignment.End
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = dotColor, shape = CircleShape)
        )

        Text(
            text = "Backend: $label",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}