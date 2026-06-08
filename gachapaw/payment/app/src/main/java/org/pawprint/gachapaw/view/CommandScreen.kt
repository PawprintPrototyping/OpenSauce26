package org.pawprint.gachapaw.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.pawprint.gachapaw.model.InputGpioState
import org.pawprint.gachapaw.ui.theme.GashapawTheme
import org.pawprint.gachapaw.viewModel.CommandViewModel

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
                ) {
                    FilledTonalButton(
                        onClick = { commandViewModel.triggerEnablePrizeDispenser() },
                        enabled = !commandUiState.isPrizeDispenserActive,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Release")
                    }
                    FilledTonalButton(
                        onClick = { commandViewModel.triggerDisablePrizeDispenser() },
                        enabled = !commandUiState.isPrizeDispenserActive,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Engage")
                    }
                }

                ControlRow(
                    title = "Neopixel LED",
                    subtitle = "Ambient Lighting",
                    icon = Icons.Default.Lightbulb,
                    isActive = false,
                ) {
                    FilledTonalButton(
                        onClick = { commandViewModel.triggerSetNeopixelColor() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Cycle Color")
                    }
                }

                val requestPaymentLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    result -> commandViewModel.handlePaymentResult(result)
                }

                ControlRow(
                    title = "Square Reader",
                    subtitle = if (commandUiState.isSquareReaderActive) "Ready for Payment" else "Disabled",
                    icon = Icons.Default.Payment,
                    isActive = commandUiState.isSquareReaderActive,
                ) {

                    val textFieldState = rememberTextFieldState(initialText = "1")
                    val currencyMask = InputTransformation.byValue { current, proposed ->
                        // Regex: optional digits, optional dot, up to 2 digits after dot
                        val regex = Regex("""^\d*\.?\d{0,2}$""")
                        if (proposed.matches(regex)) proposed else current
                    }

                    OutlinedTextField(
                        state = textFieldState,
                        prefix = { Text("$") },
                        label = { Text("Set Cost") },
                        inputTransformation = currencyMask,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )

                    FilledTonalButton(
                        onClick = { commandViewModel.triggerEnableSquareReader(requestPaymentLauncher,textFieldState.text) },
                        enabled = !commandUiState.isSquareReaderActive,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Activate")
                    }
                }

                ControlRow(
                    title = "Wait for GPIO",
                    subtitle = when (commandUiState.inputGpioState) {
                        InputGpioState.WAITING -> "Waiting for Button Press"
                        InputGpioState.NOT_CHECKING -> "Latch not set"
                        InputGpioState.LATCH_HIT -> "Button pressed!"
                    },
                    icon = Icons.Default.Payment,
                    isActive = commandUiState.inputGpioState == InputGpioState.WAITING,
                ) {
                    if (commandUiState.inputGpioState == InputGpioState.WAITING) {
                        OutlinedButton(
                            onClick = { commandViewModel.triggerCancelWaitForGpio() },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Cancel", modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    FilledTonalButton(
                        onClick = { commandViewModel.triggerWaitForGpio() },
                        enabled = commandUiState.inputGpioState != InputGpioState.WAITING,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Set Latch")
                    }
                }
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
    content: @Composable RowScope.() -> Unit
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

        content()
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun CommandScreenPreview() {
    GashapawTheme {
        CommandScreen(modifier = Modifier.padding(16.dp))
    }
}
