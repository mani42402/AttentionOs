package com.attentionos.ui.components

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attentionos.ui.theme.Coral500
import com.attentionos.ui.theme.Forest800
import com.attentionos.ui.theme.Ice500
import com.attentionos.ui.theme.LocalMotionEnabled
import com.attentionos.ui.theme.Mint300
import com.attentionos.ui.theme.Mint500
import com.attentionos.ui.theme.Sun500
import com.attentionos.ui.theme.Violet400
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun AmbientBackdrop() {
    val motionEnabled = LocalMotionEnabled.current
    val drift = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "ambient")
        val animatedDrift by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(7_000),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ambient-drift",
        )
        animatedDrift
    } else {
        0f
    }
    Canvas(
        Modifier
            .fillMaxSize()
            .alpha(0.72f),
    ) {
        drawCircle(
            color = Violet400.copy(alpha = 0.10f),
            radius = size.minDimension * 0.55f,
            center = androidx.compose.ui.geometry.Offset(
                x = size.width * (0.92f - drift * 0.07f),
                y = size.height * 0.12f,
            ),
        )
        drawCircle(
            color = Mint500.copy(alpha = 0.08f),
            radius = size.minDimension * 0.44f,
            center = androidx.compose.ui.geometry.Offset(
                x = size.width * (0.04f + drift * 0.08f),
                y = size.height * 0.78f,
            ),
        )
    }
}

@Composable
internal fun BrandMark() {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                Brush.linearGradient(listOf(Violet400, Forest800, Mint500)),
                RoundedCornerShape(15.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawCircle(Color.White.copy(alpha = 0.22f))
            drawCircle(
                Color.White,
                radius = size.minDimension * 0.19f,
            )
            drawArc(
                color = Color.White,
                startAngle = -80f,
                sweepAngle = 230f,
                useCenter = false,
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun HelperLogo() {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(13.dp),
        color = Ice500,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(23.dp)) {
                drawRoundRect(
                    color = Color.White,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.52f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.15f),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(
                    Color.White,
                    androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.82f),
                    androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.70f),
                    2.dp.toPx(),
                    StrokeCap.Round,
                )
                drawCircle(
                    Color.White,
                    radius = 1.7.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.44f),
                )
            }
        }
    }
}

@Composable
internal fun StatusBadge(label: String, positive: Boolean) {
    Surface(
        color = if (positive) Mint500.copy(alpha = 0.18f) else Sun500.copy(alpha = 0.18f),
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (positive) Mint300 else Sun500, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
internal fun OutcomeItem(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(value.toString(), style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SectionTitle(
    title: String,
    action: String?,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun EmptyActivity(hasAccess: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (hasAccess) Icons.Default.CheckCircle else Icons.Default.Info,
            contentDescription = null,
            tint = Mint500,
            modifier = Modifier.size(38.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (hasAccess) "Quiet so far" else "Waiting for notification access",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (hasAccess) "New attention decisions will appear here." else {
                "Complete setup to begin local classification."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PriorityPill(priority: String, color: Color) {
    Surface(color = color.copy(alpha = 0.13f), shape = CircleShape) {
        Text(
            text = if (priority == "SILENT") "QUIET" else priority,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
    }
}

internal fun com.attentionos.domain.NotificationCategory.readableUiName(): String =
    name.lowercase().replaceFirstChar(Char::titlecase)

@Composable
internal fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(start = 8.dp, bottom = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
            ),
        ) {
            Column(content = content)
        }
    }
}

@Composable
internal fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, Forest800)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = modernSwitchColors(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
internal fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueColor: Color = Forest800,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon, valueColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = valueColor)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun SettingIcon(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun ElevatedPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(content = content)
    }
}

@Composable
internal fun SoftDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
internal fun modernSwitchColors(accent: Color) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = accent,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
internal fun ScreenHeader(
    eyebrow: String,
    title: String,
    description: String,
    horizontalPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
    ) {
        Text(
            eyebrow,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun rememberNotificationAccess(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return remember(refresh) {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        enabled.split(':').any { component ->
            component.startsWith("${context.packageName}/")
        }
    }
}

internal fun priorityColor(priority: String): Color = when (priority) {
    "CRITICAL" -> Coral500
    "HIGH" -> Sun500
    "MEDIUM" -> Ice500
    else -> Mint500
}

internal fun formatEventTime(timestamp: Long): String =
    DateTimeFormatter.ofPattern("h:mm a")
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
