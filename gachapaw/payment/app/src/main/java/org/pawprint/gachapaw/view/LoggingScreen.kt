package org.pawprint.gachapaw.view

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.pawprint.gachapaw.model.LogSeverity
import org.pawprint.gachapaw.viewModel.LoggingViewModel

@Composable
fun LoggingScreen(modifier: Modifier) {
    val loggingViewModel: LoggingViewModel = viewModel()
    val loggingState by loggingViewModel.logState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll logic: stays locked to bottom unless the user manually scrolls up
    var autoScrollEnabled by remember { mutableStateOf(true) }
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@derivedStateOf true
            val lastVisibleItem = visibleItems.last()

            // Precise check: is the last item at the bottom of the scrollable area?
            lastVisibleItem.index == layoutInfo.totalItemsCount - 1 &&
                (lastVisibleItem.offset + lastVisibleItem.size) <= (layoutInfo.viewportEndOffset - layoutInfo.afterContentPadding + 2)
        }
    }

    // Re-enable auto-scroll when reaching the bottom (manually or via FAB)
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) autoScrollEnabled = true
    }

    // Disable auto-scroll if the user manually scrolls away from the bottom
    LaunchedEffect(listState.isScrollInProgress, isAtBottom) {
        if (listState.isScrollInProgress && !isAtBottom) {
            autoScrollEnabled = false
        }
    }

    LaunchedEffect(loggingState.size, listState.isScrollInProgress) {
        if (autoScrollEnabled && loggingState.isNotEmpty() && !listState.isScrollInProgress) {
            listState.scrollToItem(loggingState.size - 1)
        }
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
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "System Logs",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { loggingViewModel.clearLogs() }) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Terminal View
            Box(modifier = Modifier.weight(1f)) {
                OutlinedCard(
                    modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.outlinedCardColors(
                        containerColor = Color(0xFF1E1E1E), // Dark terminal background
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(width = 1.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        items(loggingState) { entry ->
                            TerminalLogEntry(entry.timestamp, entry.message, entry.severity)
                        }
                    }
                }

                // Jump to Bottom FAB
                Box(modifier = Modifier.fillMaxSize()) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !isAtBottom,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        SmallFloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    if (loggingState.isNotEmpty()) {
                                        listState.animateScrollToItem(loggingState.size - 1)
                                    }
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Jump to Bottom")
                        }
                    }
                }
            }

            // Status Footer
            Text(
                text = "Log Stream: ${if (autoScrollEnabled) "Active (Auto-scroll)" else "Paused"}",
                style = MaterialTheme.typography.labelMedium,
                color = if (autoScrollEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun TerminalLogEntry(timeStamp: String, message: String, severity: LogSeverity) {
    val shortTime = timeStamp.substringAfter("T").substringBefore(".")
    val contentColor = when (severity) {
        LogSeverity.INFO -> Color(0xFF00ACC1) // Cyan-ish
        LogSeverity.WARNING -> Color(0xFFFFB300) // Amber/Yellow
        LogSeverity.ERROR -> Color(0xFFE53935) // Red
        LogSeverity.DEBUG -> Color(0xFF757575) // Gray
    }

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
            color = contentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}
