package org.pawprint.gachapaw

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.pawprint.gachapaw.model.LogSeverity
import org.pawprint.gachapaw.service.GpioRepository
import org.pawprint.gachapaw.service.GpioService
import org.pawprint.gachapaw.ui.theme.GashapawTheme
import org.pawprint.gachapaw.view.CommandScreen
import org.pawprint.gachapaw.view.LoggingScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val loggingRepository = (application as PawApplication).loggingRepository
        loggingRepository.addLog("MainActivity: Application started", LogSeverity.DEBUG)

        setContent {
            val context = LocalContext.current
            val gpioRepository = (application as PawApplication).gpioRepository
            // Permission checks
            var hasNotificationPermission by remember {
                mutableStateOf(isPostNotificationsGranted(context))
            }
            LaunchedEffect(hasNotificationPermission) {
                // Evaluate every time hasNotificationPermission changes
                if (hasNotificationPermission) {
                    startGpioService()
                }
            }
            val requestPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission())
            { isGranted ->
                hasNotificationPermission = isGranted
                if (isGranted) {
                    loggingRepository.addLog("MainActivity: Notification permission granted", LogSeverity.INFO)
                } else {
                    loggingRepository.addLog("MainActivity: Notification permission denied", LogSeverity.WARNING)
                }
            }
            LaunchedEffect(Unit) {
                // Launch once at when the activity is created
                if (!hasNotificationPermission) {
                    loggingRepository.addLog("MainActivity: Requesting notification permission", LogSeverity.DEBUG)
                    requestPermissionLauncher.launch(POST_NOTIFICATIONS)
                }
            }
            GashapawTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        GashapawScreen()
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                            .fillMaxSize()
                    ) {
                        if (hasNotificationPermission) {
                            DebugScreen(gpioRepository)
                        } else {
                            FilledTonalButton(
                                onClick = { requestPermissionLauncher.launch(POST_NOTIFICATIONS) },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Request Permissions")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isPostNotificationsGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startGpioService() {
        val loggingRepository = (application as PawApplication).loggingRepository
        loggingRepository.addLog("MainActivity: Starting GpioService", LogSeverity.INFO)
        val intent = Intent(this, GpioService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GashapawScreen() {
    CenterAlignedTopAppBar(
        title = {
            Text(
                "GASHAPAW DASHBOARD",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.primary
        )
    )
}


@Composable
fun DebugScreen(gpioRepository: GpioRepository) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.weight(0.4f)) {
            LoggingScreen(modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.weight(0.6f)) {
            CommandScreen(modifier = Modifier.fillMaxSize(), gpioRepository)
        }
    }
}
