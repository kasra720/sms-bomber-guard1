package com.smsbomberguard.app

/**
 * MainScreen.kt
 * -----------------------------------------------------------
 * The whole app UI in one screen:
 *   1. Hero status card       - Protected / Blocking now / Off
 *   2. Protection toggle      - user enable/disable, default ON
 *   3. Duration control       - user-adjustable, default 10 min
 *   4. Silent log             - withheld messages, shown quietly,
 *                                never as a notification burst
 *
 * All state is reactive (StateFlow / collectAsState + a 1s ticker
 * for the countdown), so the UI never needs manual refresh.
 */

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsbomberguard.app.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private enum class GuardState { PROTECTED, BLOCKING, OFF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var enabled by remember { mutableStateOf(AppSettings.current.protectionEnabled) }
    var durationMinutes by remember { mutableFloatStateOf(AppSettings.current.blockDurationMinutes.toFloat()) }
    val logItems by SilentSmsLog.items.collectAsState()

    // 1-second ticker to keep the "blocking" countdown live.
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick = System.currentTimeMillis()
        }
    }

    val limiter = remember { RateLimiterProvider.get() }
    val isBlocking = tick >= 0 && enabled && limiter.isCurrentlyBlocked()
    val remainingSec = if (isBlocking) limiter.remainingMillis() / 1000 else 0L

    val guardState = when {
        !enabled -> GuardState.OFF
        isBlocking -> GuardState.BLOCKING
        else -> GuardState.PROTECTED
    }

    var notifAccessGranted by remember { mutableStateOf(NotificationAccess.isGranted(context)) }
    // Re-check whenever the user comes back from the system settings screen.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifAccessGranted = NotificationAccess.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            if (!notifAccessGranted) {
                item {
                    NotificationAccessBanner(
                        onGrantClick = { NotificationAccess.openSettings(context) }
                    )
                }
            }

            item { HeroStatusCard(guardState, remainingSec) }

            item {
                SettingsCard(
                    enabled = enabled,
                    durationMinutes = durationMinutes,
                    onEnabledChange = { newValue ->
                        enabled = newValue
                        scope.launch2 { AppSettings.setProtectionEnabled(context, newValue) }
                    },
                    onDurationChange = { newValue ->
                        durationMinutes = newValue
                    },
                    onDurationChangeFinished = {
                        scope.launch2 { AppSettings.setBlockDurationMinutes(context, durationMinutes.toInt()) }
                    }
                )
            }

            item {
                Text(
                    "Silent Log",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    "Messages withheld during an attack. They never trigger a " +
                        "notification, even after the block window ends - review them here whenever you like.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            if (logItems.isEmpty()) {
                item { EmptyLogState() }
            } else {
                items(logItems.reversed()) { item ->
                    LogRow(item)
                }
            }
        }
    }
}

@Composable
private fun NotificationAccessBanner(onGrantClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                "Notification access needed",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Without this, your default messaging app will still show its own " +
                    "notification during an attack - this app can only withhold its own. " +
                    "Grant access once so blocking works completely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onGrantClick) {
                Text("Grant Notification Access")
            }
        }
    }
}

@Composable
private fun HeroStatusCard(state: GuardState, remainingSec: Long) {
    val (bg, fg, title, subtitle, icon) = when (state) {
        GuardState.PROTECTED -> HeroStyle(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Protected",
            "Watching for burst SMS activity",
            Icons.Filled.Shield
        )
        GuardState.BLOCKING -> HeroStyle(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Blocking now",
            "Suspicious burst detected - withholding new messages for ${formatDuration(remainingSec)}",
            Icons.Filled.Warning
        )
        GuardState.OFF -> HeroStyle(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Protection off",
            "All messages are shown normally",
            Icons.Filled.CheckCircle
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                AnimatedContent(targetState = title, label = "hero-title") { t ->
                    Text(t, style = MaterialTheme.typography.headlineMedium, color = fg)
                }
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = fg)
            }
        }
    }
}

private data class HeroStyle(
    val bg: Color,
    val fg: Color,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun SettingsCard(
    enabled: Boolean,
    durationMinutes: Float,
    onEnabledChange: (Boolean) -> Unit,
    onDurationChange: (Float) -> Unit,
    onDurationChangeFinished: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Enable protection", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Detect and silently withhold bomber-style SMS bursts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabledChange)
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-unblock after", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${durationMinutes.toInt()} min",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = durationMinutes,
                onValueChange = onDurationChange,
                onValueChangeFinished = onDurationChangeFinished,
                valueRange = 1f..60f,
                steps = 58,
                enabled = enabled
            )
            Text(
                "How long a burst is held before it's silently released into the log below. Default: 10 minutes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyLogState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "No blocked messages yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LogRow(item: QuarantinedSms) {
    val time = remember(item.timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.sender, style = MaterialTheme.typography.titleMedium)
                Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            Text(item.body, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}

// Small helper so this file doesn't need to import kotlinx.coroutines.launch explicitly at call sites above.
private fun kotlinx.coroutines.CoroutineScope.launch2(block: suspend () -> Unit) {
    kotlinx.coroutines.launch { block() }
}
