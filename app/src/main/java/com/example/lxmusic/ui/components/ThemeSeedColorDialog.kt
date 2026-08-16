package com.example.lxmusic.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.example.lxmusic.ui.theme.PRESET_SEED_COLORS

/**
 * HSV 色相/饱和度/明度选择器（移植自 NeriPlayer HsvPicker）
 * @param onColorChanged 回调返回 6 位 HEX（不含 #）
 */
@Composable
fun HsvPicker(
    initialHex: String = "0061A4",
    onColorChanged: (String) -> Unit
) {
    // 初始把 HEX 转为 HSV
    fun hexToHsv(hex: String): FloatArray {
        val c = try { Color(("#$hex").toColorInt()) } catch (_: Throwable) { Color(0xFF0061A4) }
        val argb = c.toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(r, g, b, hsv)
        return hsv
    }

    var hsv by remember(initialHex) { mutableStateOf(hexToHsv(initialHex)) }
    val previewColor = remember(hsv) {
        Color(AndroidColor.HSVToColor(hsv))
    }
    val hex = remember(previewColor) {
        val a = previewColor.toArgb()
        val r = (a shr 16) and 0xFF
        val g = (a shr 8) and 0xFF
        val b = a and 0xFF
        String.format("%02X%02X%02X", r, g, b)
    }

    LaunchedEffect(hex) { onColorChanged(hex) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 预览
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(previewColor)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape)
            )
            Spacer(Modifier.size(12.dp))
            Text("#$hex", fontFamily = FontFamily.Monospace)
        }

        // Hue 0..360
        Text("色相", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = hsv[0],
            onValueChange = { hsv = floatArrayOf(it, hsv[1], hsv[2]) },
            valueRange = 0f..360f
        )

        // Saturation 0..1
        Text("饱和度", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = hsv[1],
            onValueChange = { hsv = floatArrayOf(hsv[0], it, hsv[2]) },
            valueRange = 0f..1f
        )

        // Value 0..1
        Text("明度", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = hsv[2],
            onValueChange = { hsv = floatArrayOf(hsv[0], hsv[1], it) },
            valueRange = 0f..1f
        )
    }
}

/**
 * 主题种子色选择对话框（移植自 NeriPlayer ColorPickerDialog）
 * @param currentHex 当前种子色（6 位 HEX）
 * @param palette 自定义色板（不含预设色）
 * @param onColorSelected 选择新种子色
 * @param onAddColor 把当前颜色加入自定义色板
 * @param onRemoveColor 从自定义色板删除颜色
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeSeedColorDialog(
    currentHex: String,
    palette: List<String>,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onRemoveColor: (String) -> Unit
) {
    var pickedHex by remember(currentHex) { mutableStateOf(currentHex.uppercase()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("选择主题颜色") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 预设色板 + 自定义色板
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PRESET_SEED_COLORS.forEach { hex ->
                        ColorDot(
                            hex = hex,
                            isSelected = currentHex.equals(hex, ignoreCase = true),
                            onClick = { pickedHex = hex.uppercase() }
                        )
                    }
                    palette.forEach { hex ->
                        ColorDot(
                            hex = hex,
                            isSelected = currentHex.equals(hex, ignoreCase = true),
                            onClick = { pickedHex = hex.uppercase() },
                            onRemove = { onRemoveColor(hex) }
                        )
                    }
                }

                // HSV 选择器
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "自定义颜色",
                        style = MaterialTheme.typography.titleSmall
                    )
                    HsvPicker(
                        initialHex = currentHex,
                        onColorChanged = { pickedHex = it.uppercase() }
                    )
                }

                val existsInPalette = palette.any { it.equals(pickedHex, ignoreCase = true) } ||
                    PRESET_SEED_COLORS.any { it.equals(pickedHex, ignoreCase = true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onAddColor(pickedHex) },
                        enabled = !existsInPalette,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("加入色板")
                    }
                    Button(
                        onClick = { onColorSelected(pickedHex) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("应用")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ColorDot(
    hex: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    val color = Color(("#$hex").toColorInt())
    val clickableModifier = if (onRemove != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onRemove)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(color)
            .then(clickableModifier),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            val contentColor = if (ColorUtils.calculateLuminance(color.toArgb()) > 0.5) {
                Color.Black
            } else {
                Color.White
            }
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选中",
                tint = contentColor
            )
        }

        if (onRemove != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "删除颜色",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
