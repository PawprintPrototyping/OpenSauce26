package com.pawprint.gachapaw.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pawprint.gachapaw.ui.theme.GashapawTheme
import com.pawprint.gachapaw.viewModel.LoggingViewModel

@Composable
fun LoggingScreen(modifier: Modifier) {
    val loggingViewModel: LoggingViewModel = viewModel()
    val loggingState by loggingViewModel.logState.collectAsStateWithLifecycle()

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
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "System Logs",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Terminal View
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = Color(0xFF1E1E1E), // Dark terminal background
                ),
                border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    reverseLayout = true
                ) {
                    items(loggingState) { entry ->
                        TerminalLogEntry(entry.timestamp, entry.logLine)
                    }
                }
            }

            // Status Footer
            Text(
                text = "Log Stream: Active",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun TerminalLogEntry(timeStamp: String, message: String) {
    val shortTime = timeStamp.substringAfter("T").substringBefore(".")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "[$shortTime]",
            color = Color(0xFF888888),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(
            text = message,
            color = Color(0xFFD4D4D4),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 600)
@Composable
fun LoggingScreenPreview() {
    GashapawTheme {
        LoggingScreen(modifier = Modifier.padding(16.dp))
    }
}
