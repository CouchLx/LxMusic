package com.example.lxmusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 声音设置 / 均衡器两个页面共用的控件。
 * 视觉参考 BoomingMusic 的同名页面：圆角卡片 + 标题行图标 + 两端对齐的数值文本。
 */

/** 页面顶栏：返回 + 标题 + 右侧操作 */
@Composable
fun AudioPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp)
        )
        actions()
    }
}

/** 整页容器：状态栏/导航栏安全区 + 顶栏 + 内容 */
@Composable
fun AudioSettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AudioPageTopBar(title = title, onBack = onBack, actions = actions)
        content()
    }
}

/** 带标题的圆角卡片（对应 BoomingMusic 的 TitledCard） */
@Composable
fun AudioSettingsCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            content = content
        )
    }
}

/** 右侧数值文本（固定宽度，避免滑动时行内跳动） */
@Composable
fun AudioValueText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary
) {
    Text(
        text = text,
        color = color,
        maxLines = 1,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier.width(56.dp)
    )
}

/** 带副标题的开关行 */
@Composable
fun AudioSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

/** 专业模式参数滑杆：左标签，右数值 */
@Composable
fun AudioParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    unit: String = "",
    format: String = "%.1f",
    enabled: Boolean = true,
    onValueChangeFinished: (Float) -> Unit
) {
    var localValue by androidx.compose.runtime.remember(value) {
        androidx.compose.runtime.mutableFloatStateOf(value)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.ROOT, format, localValue) + unit,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 均衡器响应曲线（移植自 BoomingMusic 的 EqualizerWaveform） */
@Composable
fun EqualizerWaveform(
    bands: List<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = color.copy(alpha = 0.2f),
    strokeWidth: Float = 3f,
    smoothingTension: Float = 0.2f
) {
    if (bands.isEmpty()) return

    val contentAlpha = if (enabled) 1f else 0.5f
    val actualColor = color.copy(alpha = color.alpha * contentAlpha)
    val actualFillColor = fillColor.copy(alpha = fillColor.alpha * contentAlpha)

    val minGain = valueRange.start
    val maxGain = valueRange.endInclusive
    val rangeSize = (maxGain - minGain).coerceAtLeast(0.001f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // 0dB 基线
        if (0f in valueRange) {
            val baselineY = height - ((0f - minGain) / rangeSize) * height
            drawLine(
                color = actualColor.copy(alpha = 0.12f),
                start = Offset(0f, baselineY),
                end = Offset(width, baselineY),
                strokeWidth = 1f
            )
        }

        val points = bands.mapIndexed { index, bandValue ->
            val x = if (bands.size > 1) {
                (index.toFloat() / (bands.size - 1)) * width
            } else {
                width / 2f
            }
            val y = height - ((bandValue - minGain) / rangeSize) * height
            Offset(x, y)
        }

        if (points.size >= 2) {
            val strokePath = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val prev = if (i > 0) points[i - 1] else p0
                    val next = if (i < points.size - 2) points[i + 2] else p1
                    val cp1x = p0.x + (p1.x - prev.x) * smoothingTension
                    val cp1y = p0.y + (p1.y - prev.y) * smoothingTension
                    val cp2x = p1.x - (next.x - p0.x) * smoothingTension
                    val cp2y = p1.y - (next.y - p0.y) * smoothingTension
                    cubicTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(actualFillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )
            drawPath(
                path = strokePath,
                color = actualColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
            points.forEach { point ->
                drawCircle(color = actualColor, radius = strokeWidth * 1.5f, center = point)
            }
        }
    }
}

/** 小圆角标签（段数 / 当前预设等） */
@Composable
fun AudioChip(
    text: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}

/** 百分比文本：0..1000 的强度值 → 0~100% */
fun effectPercent(value: Float, range: ClosedFloatingPointRange<Float>): String {
    val span = (range.endInclusive - range.start).coerceAtLeast(0.001f)
    return "${(((value - range.start) / span) * 100f).roundToInt()}%"
}

/** 页面顶部与内容之间的间距 */
@Composable
fun AudioPageSpacer() {
    Spacer(modifier = Modifier.height(4.dp))
}
