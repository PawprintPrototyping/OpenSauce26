package com.pawprint.gachapaw.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pawprint.gachapaw.ui.theme.GashapawTheme
import com.pawprint.gachapaw.viewModel.CommandViewModel

@Composable
fun CommandScreen(modifier: Modifier) {
    val commandViewModel: CommandViewModel = viewModel()
    val commandUiState by commandViewModel.uiState.collectAsStateWithLifecycle()

    OutlinedCard(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "Control Panel",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Control Sections
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f)
            ) {
                ControlRow(
                    title = "Prize Dispenser",
                    subtitle = if (commandUiState.isPrizeDispenserActive) "Active" else "Idle",
                    icon = Icons.Default.Build,
                    isActive = commandUiState.isPrizeDispenserActive,
                    onClick = { commandViewModel.triggerEnablePrizeDispenser() },
                    buttonText = "Release"
                )

                ControlRow(
                    title = "Neopixel LED",
                    subtitle = "Ambient Lighting",
                    icon = Icons.Default.Lightbulb,
                    isActive = false,
                    onClick = { commandViewModel.triggerSetNeopixelColor() },
                    buttonText = "Cycle Color"
                )

                ControlRow(
                    title = "Square Reader",
                    subtitle = if (commandUiState.isSquareReaderActive) "Ready for Payment" else "Disabled",
                    icon = Icons.Default.Payment,
                    isActive = commandUiState.isSquareReaderActive,
                    onClick = { commandViewModel.triggerEnableSquareReader() },
                    buttonText = "Activate"
                )
            }

            // Status Footer
            Text(
                text = "System Status: Online",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
fun ControlRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    buttonText: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FilledTonalButton(
            onClick = onClick,
            enabled = !isActive,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(buttonText)
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun CommandScreenPreview() {
    GashapawTheme {
        CommandScreen(modifier = Modifier.padding(16.dp))
    }
}
