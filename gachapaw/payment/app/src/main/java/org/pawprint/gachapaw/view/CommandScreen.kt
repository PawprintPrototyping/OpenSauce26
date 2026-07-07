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
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.pawprint.gachapaw.model.ServiceState
import org.pawprint.gachapaw.model.InputGpioState
import org.pawprint.gachapaw.model.TransactionState
import org.pawprint.gachapaw.viewModel.CommandViewModel
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.pawprint.gachapaw.service.GpioRepository

@Composable
fun CommandScreen(modifier: Modifier, gpioRepository: GpioRepository) {
    val context = LocalContext.current
    val pawApp = context.applicationContext as org.pawprint.gachapaw.PawApplication
    val squarePaymentRepository = pawApp.squarePaymentRepository
    val loggingRepository = pawApp.loggingRepository
    val commandViewModel: CommandViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CommandViewModel(gpioRepository, squarePaymentRepository, loggingRepository)
            }
        }
    )
    val isConnected by commandViewModel.isConnected.collectAsStateWithLifecycle(false)
    val commandUiState by commandViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        commandViewModel.activityLaunchEvents.collect { context.startActivity(it) }
    }
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
                if (isConnected) {
                    Text(
                        "(Connected)",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            if (isConnected) {
                if (commandUiState.transactionState == TransactionState.MAINTENANCE) {
                    DebugScreen(modifier.weight(1f), commandViewModel, commandUiState)
                } else {
                    ProdScreen(modifier.weight(1f), commandViewModel)
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Service not Connected!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "The background GPIO service is still initializing...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProdScreen(
    modifier: Modifier = Modifier,
    commandViewModel: CommandViewModel
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Current State : ")
            Text(text = commandViewModel.uiState.collectAsState().value.transactionState.toString())
        }
        FilledTonalButton(
            onClick = { commandViewModel.resetPaymentFlow() },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Reset Payment Flow")
        }
        FilledTonalButton(
            onClick = { commandViewModel.enterDebugMode() },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Enter Debug Mode")
        }
    }
}

@Composable
fun DebugScreen(
    modifier: Modifier = Modifier,
    commandViewModel: CommandViewModel,
    serviceState: ServiceState
) {
    // Control Sections
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = modifier
    ) {
        ControlRow(
            title = "Prize Dispenser",
            subtitle = if (serviceState.isPrizeDispenserActive) "Active" else "Idle",
            icon = Icons.Default.Build,
            isActive = serviceState.isPrizeDispenserActive,
        ) {
            FilledTonalButton(
                onClick = { commandViewModel.unlockPrizeDispenser() },
                enabled = !serviceState.isPrizeDispenserActive,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Release")
            }
            FilledTonalButton(
                onClick = { commandViewModel.lockPrizeDispenser() },
                enabled = !serviceState.isPrizeDispenserActive,
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
                onClick = { commandViewModel.setRandomNeopixelColor() },
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
            subtitle = if (serviceState.isSquareReaderActive) "Ready for Payment" else "Disabled",
            icon = Icons.Default.Payment,
            isActive = serviceState.isSquareReaderActive,
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
                onClick = {
                        commandViewModel.launchSquareReaderActivity(
                            requestPaymentLauncher,
                            textFieldState.text
                        )
                },
                enabled = !serviceState.isSquareReaderActive,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Activate")
            }
        }

        ControlRow(
            title = "Wait for GPIO",
            subtitle = when (serviceState.inputGpioState) {
                InputGpioState.WAITING -> "Waiting for Button Press"
                InputGpioState.NOT_CHECKING -> "Latch not set"
                InputGpioState.LATCH_HIT -> "Button pressed!"
            },
            icon = Icons.Default.Payment,
            isActive = serviceState.inputGpioState == InputGpioState.WAITING,
        ) {
            if (serviceState.inputGpioState == InputGpioState.WAITING) {
                OutlinedButton(
                    onClick = { commandViewModel.cancelWaitForButtonPress() },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Cancel", modifier = Modifier.padding(start = 4.dp))
                }
            }

            FilledTonalButton(
                onClick = { commandViewModel.waitForButtonPress() },
                enabled = serviceState.inputGpioState != InputGpioState.WAITING,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Set Latch")
            }
        }

        FilledTonalButton(
            onClick = { commandViewModel.enterProdMode() },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Enter Running Mode")
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
