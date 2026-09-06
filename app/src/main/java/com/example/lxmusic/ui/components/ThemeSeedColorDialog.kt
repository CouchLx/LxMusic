@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@file:Suppress("DEPRECATION")

package com.example.lxmusic.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import com.example.lxmusic.ui.theme.resolvePaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 预设精选主题色与展示名称
 */
data class SeedColorPreset(
    val hex: String,
    val name: String
)

val EXTENDED_SEED_PRESETS = listOf(
    SeedColorPreset("0061A4", "经典蓝"),
    SeedColorPreset("6750A4", "群青紫"),
    SeedColorPreset("00897B", "薄荷青"),
    SeedColorPreset("388E3C", "森林绿"),
    SeedColorPreset("E65100", "熔岩橙"),
    SeedColorPreset("B3261E", "珊瑚红"),
    SeedColorPreset("C425A8", "霓虹粉"),
    SeedColorPreset("00838F", "深海青"),
    SeedColorPreset("3F51B5", "暮夜蓝"),
    SeedColorPreset("D81B60", "玫瑰红"),
    SeedColorPreset("FB8C00", "琥珀黄"),
    SeedColorPreset("00C853", "极光绿"),
    SeedColorPreset("7E57C2", "薰衣草"),
    SeedColorPreset("0288D1", "青空蓝"),
    SeedColorPreset("F57F17", "暖阳金"),
    SeedColorPreset("546E7A", "星空灰")
)

/**
 * 解析并清理 HEX 字符串为合法 6 位色码
 */
fun cleanHexColor(raw: String): String {
    val clean = raw.trim().removePrefix("#").uppercase()
    return if (clean.length == 6 && clean.all { it.isDigit() || it in 'A'..'F' }) clean else "0061A4"
}

/**
 * HEX 转换为 HSV 浮点数组 (Hue 0..360, Saturation 0..1, Value 0..1)
 */
fun hexToHsv(hex: String): FloatArray {
    val c = try { Color(("#${cleanHexColor(hex)}").toColorInt()) } catch (_: Throwable) { Color(0xFF0061A4) }
    val argb = c.toArgb()
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    return hsv
}

/**
 * HSV 转换为 6 位十六进制色码 (不含 #)
 */
fun hsvToHex(hsv: FloatArray): String {
    val argb = AndroidColor.HSVToColor(hsv)
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return String.format("%02X%02X%02X", r, g, b)
}

/**
 * 原生 Material 3 风格的高级主题颜色配置面板 (BottomSheet)
 *
 * @param currentHex 当前种子色（6 位 HEX）
 * @param palette 用户自定义收藏色板
 * @param paletteStyle 当前使用的 Material 3 取色风格
 * @param onPaletteStyleChange 取色风格变更回调
 * @param onDismiss 关闭面板
 * @param onColorSelected 应用新主题色
 * @param onAddColor 将颜色加入收藏色板
 * @param onRemoveColor 从收藏色板移除颜色
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ThemeSeedColorDialog(
    currentHex: String,
    palette: List<String>,
    paletteStyle: String = "TonalSpot",
    onPaletteStyleChange: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit,
    onAddColor: (String) -> Unit,
    onRemoveColor: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val isDarkTheme = isSystemInDarkTheme()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // 独立的 HSV 浮点状态（解决滑块联动重置/精度丢失问题）
    val initialHsv = remember(currentHex) { hexToHsv(currentHex) }
    var hue by remember(currentHex) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(currentHex) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(currentHex) { mutableFloatStateOf(initialHsv[2]) }

    var selectedStyle by remember(paletteStyle) { mutableStateOf(paletteStyle) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: 预设配色, 1: 自由调色

    var hexInputText by remember(currentHex) { mutableStateOf(cleanHexColor(currentHex)) }
    var hexInputError by remember { mutableStateOf(false) }

    // 从独立状态派生出当前拾取的 6 位 HEX 与 Color（单向无环流动）
    val pickedHex = remember(hue, saturation, value) {
        hsvToHex(floatArrayOf(hue, saturation, value))
    }
    val currentColor = remember(hue, saturation, value) {
        Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
    }

    // 通过 MaterialKolor 实时生成真实的 DynamicColorScheme
    val previewScheme = rememberDynamicColorScheme(
        seedColor = currentColor,
        isDark = isDarkTheme,
        style = resolvePaletteStyle(selectedStyle)
    )

    // 检查是否已经在收藏色板或预设中
    val isAlreadyInPalette = remember(pickedHex, palette) {
        palette.any { it.equals(pickedHex, ignoreCase = true) } ||
                EXTENDED_SEED_PRESETS.any { it.hex.equals(pickedHex, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 顶部标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "自定义主题色彩",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "基于 Google Material You 算法动态推导整套深浅色系",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }
                        onDismiss()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 可滚动的主内容区
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 纯净 Material 3 主题联动预览卡片
                ThemeRealtimePreviewCard(
                    seedHex = pickedHex,
                    seedColor = currentColor,
                    primaryColor = previewScheme.primary,
                    onPrimaryColor = previewScheme.onPrimary,
                    primaryContainer = previewScheme.primaryContainer,
                    onPrimaryContainer = previewScheme.onPrimaryContainer,
                    surfaceColor = previewScheme.surfaceContainer,
                    onSurfaceColor = previewScheme.onSurface,
                    currentStyle = selectedStyle
                )

                // 2. 取色算法风格选择（水平平滑滚动，单行不换行，尺寸整齐）
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "取色算法风格",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val styleList = listOf(
                        "TonalSpot" to "经典均衡",
                        "Fidelity" to "色彩保真",
                        "Vibrant" to "鲜艳活力",
                        "Expressive" to "丰富表现",
                        "Neutral" to "中性内敛"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        styleList.forEach { (styleKey, styleLabel) ->
                            val isSelected = selectedStyle == styleKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedStyle = styleKey
                                    onPaletteStyleChange(styleKey)
                                },
                                label = {
                                    Text(
                                        text = styleLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                }

                // 3. Tab 分段：[ 精选配色 ] / [ 自由调色 ]
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary,
                            height = 3.dp
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("精选配色", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("自由调色", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }

                // 4. Tab 1: 预设配色与收藏色板
                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // 预设色彩网格 (4列)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EXTENDED_SEED_PRESETS.forEach { preset ->
                                val isSelected = pickedHex.equals(preset.hex, ignoreCase = true)
                                PresetColorCircleItem(
                                    preset = preset,
                                    isSelected = isSelected,
                                    onClick = {
                                        val newHsv = hexToHsv(preset.hex)
                                        hue = newHsv[0]
                                        saturation = newHsv[1]
                                        value = newHsv[2]
                                        hexInputText = preset.hex.uppercase()
                                        hexInputError = false
                                    }
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )

                        // 我的收藏色板
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "我的收藏色板 (${palette.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (palette.isNotEmpty()) {
                                Text(
                                    text = "长按或点击右侧可删除",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        if (palette.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无收藏色彩，在“自由调色”中点击“收藏颜色”即可保存",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        } else {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.Start),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                palette.forEach { favHex ->
                                    val isSelected = pickedHex.equals(favHex, ignoreCase = true)
                                    FavoriteColorChipItem(
                                        hex = favHex,
                                        isSelected = isSelected,
                                        onClick = {
                                            val newHsv = hexToHsv(favHex)
                                            hue = newHsv[0]
                                            saturation = newHsv[1]
                                            value = newHsv[2]
                                            hexInputText = favHex.uppercase()
                                            hexInputError = false
                                        },
                                        onRemove = {
                                            onRemoveColor(favHex)
                                            Toast.makeText(context, "已从收藏中移除 #$favHex", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Tab 2: 自由调色 (独立平滑渐变条 + 独立状态 + HEX输入)
                if (selectedTab == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // HEX 直接输入与快捷操作栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = hexInputText,
                                onValueChange = { input ->
                                    val formatted = input.trim().removePrefix("#").take(6).uppercase()
                                    hexInputText = formatted
                                    if (formatted.length == 6 && formatted.all { it.isDigit() || it in 'A'..'F' }) {
                                        hexInputError = false
                                        val newHsv = hexToHsv(formatted)
                                        hue = newHsv[0]
                                        saturation = newHsv[1]
                                        value = newHsv[2]
                                    } else {
                                        hexInputError = formatted.length == 6
                                    }
                                },
                                label = { Text("颜色代码 (HEX)") },
                                prefix = { Text("#", fontWeight = FontWeight.Bold) },
                                isError = hexInputError,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    focusedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )

                            // 粘贴剪贴板
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                    if (!clip.isNullOrBlank()) {
                                        val clean = clip.removePrefix("#").take(6).uppercase()
                                        if (clean.length == 6 && clean.all { it.isDigit() || it in 'A'..'F' }) {
                                            val newHsv = hexToHsv(clean)
                                            hue = newHsv[0]
                                            saturation = newHsv[1]
                                            value = newHsv[2]
                                            hexInputText = clean
                                            hexInputError = false
                                            Toast.makeText(context, "已粘贴颜色 #$clean", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "剪贴板不是合法的 6 位十六进制色码", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "粘贴色码", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            // 随机骰子灵感
                            IconButton(
                                onClick = {
                                    val randomHex = String.format("%02X%02X%02X", Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
                                    val newHsv = hexToHsv(randomHex)
                                    hue = newHsv[0]
                                    saturation = newHsv[1]
                                    value = newHsv[2]
                                    hexInputText = randomHex
                                    hexInputError = false
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Icon(Icons.Default.Casino, contentDescription = "随机灵感", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // 1. 色相（Hue: 0..360）交互式彩虹光谱滑块
                        InteractiveColorBar(
                            label = "色相 (Hue)",
                            valueDisplay = "${hue.toInt()}°",
                            progress = hue / 360f,
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Red, Color.Yellow, Color.Green,
                                    Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                )
                            ),
                            thumbColor = currentColor,
                            onProgressChange = { newProg ->
                                hue = (newProg * 360f).coerceIn(0f, 360f)
                                hexInputText = hsvToHex(floatArrayOf(hue, saturation, value))
                                hexInputError = false
                            }
                        )

                        // 2. 饱和度（Saturation: 0..1）交互式渐变滑块
                        val pureHueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, value.coerceAtLeast(0.3f))))
                        val grayColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 0f, value.coerceAtLeast(0.3f))))

                        InteractiveColorBar(
                            label = "饱和度 (Saturation)",
                            valueDisplay = "${(saturation * 100).toInt()}%",
                            progress = saturation,
                            brush = Brush.horizontalGradient(listOf(grayColor, pureHueColor)),
                            thumbColor = currentColor,
                            onProgressChange = { newProg ->
                                saturation = newProg.coerceIn(0f, 1f)
                                hexInputText = hsvToHex(floatArrayOf(hue, saturation, value))
                                hexInputError = false
                            }
                        )

                        // 3. 明度（Brightness: 0..1）交互式渐变滑块
                        val fullBrightColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, saturation.coerceAtLeast(0.2f), 1f)))

                        InteractiveColorBar(
                            label = "明度 (Brightness)",
                            valueDisplay = "${(value * 100).toInt()}%",
                            progress = value,
                            brush = Brush.horizontalGradient(listOf(Color.Black, fullBrightColor)),
                            thumbColor = currentColor,
                            onProgressChange = { newProg ->
                                value = newProg.coerceIn(0f, 1f)
                                hexInputText = hsvToHex(floatArrayOf(hue, saturation, value))
                                hexInputError = false
                            }
                        )

                        // 快速风格微调胶囊
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "快捷色彩风格",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val moodOptions = listOf(
                                    "鲜艳保真" to Pair(1.0f, 0.95f),
                                    "经典均衡" to Pair(0.75f, 0.85f),
                                    "柔和莫兰迪" to Pair(0.45f, 0.90f),
                                    "深邃沉稳" to Pair(0.85f, 0.55f)
                                )
                                moodOptions.forEach { (label, satVal) ->
                                    Surface(
                                        onClick = {
                                            saturation = satVal.first
                                            value = satVal.second
                                            hexInputText = hsvToHex(floatArrayOf(hue, saturation, value))
                                            hexInputError = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            // 底部常驻操作栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 收藏按钮
                OutlinedButton(
                    onClick = {
                        onAddColor(pickedHex)
                        Toast.makeText(context, "已将 #$pickedHex 加入收藏色板", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isAlreadyInPalette,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isAlreadyInPalette) "已在色板" else "收藏颜色")
                }

                // 应用主题主按钮 (采用真实推导出的 primary 颜色)
                Button(
                    onClick = {
                        coroutineScope.launch { sheetState.hide() }
                        onColorSelected(pickedHex)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewScheme.primary,
                        contentColor = previewScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("应用主题", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * 实时 Material 3 全色系联动预览微缩体验卡片（纯净无毛边）
 */
@Composable
private fun ThemeRealtimePreviewCard(
    seedHex: String,
    seedColor: Color,
    primaryColor: Color,
    onPrimaryColor: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    surfaceColor: Color,
    onSurfaceColor: Color,
    currentStyle: String
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = surfaceColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部：色卡与 HEX 复制条（干净正圆无瑕疵）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(seedColor)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "#$seedHex",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = onSurfaceColor
                        )
                        Text(
                            text = "算法: $currentStyle",
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceColor.copy(alpha = 0.65f)
                        )
                    }
                }

                // 复制色码按钮
                Surface(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("SeedColor", "#$seedHex"))
                        Toast.makeText(context, "已复制色码 #$seedHex", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(14.dp), tint = onPrimaryContainer)
                        Spacer(Modifier.width(4.dp))
                        Text("复制", style = MaterialTheme.typography.labelSmall, color = onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 模拟 Material 3 主题组件实时效果（宽裕水平排版，不折行）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // 模拟播放按钮
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(primaryColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = onPrimaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "主题自适应效果",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = onSurfaceColor
                            )
                            Text(
                                text = "组件与背景自适应当前色彩体系",
                                style = MaterialTheme.typography.labelSmall,
                                color = onSurfaceColor.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    // 高亮标签
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor
                    ) {
                        Text(
                            text = "高光色",
                            style = MaterialTheme.typography.labelSmall,
                            color = onPrimaryColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 交互式连续渐变色彩条组件（带自绘制拖拽环形手柄，平滑跟手无抖动）
 */
@Composable
private fun InteractiveColorBar(
    label: String,
    valueDisplay: String,
    progress: Float,
    brush: Brush,
    thumbColor: Color,
    onProgressChange: (Float) -> Unit
) {
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val thumbRadiusDp = 12.dp
            val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
            val usableWidthPx = (totalWidthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

            // 渐变轨道
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val newProgress = ((offset.x - thumbRadiusPx) / usableWidthPx).coerceIn(0f, 1f)
                            onProgressChange(newProgress)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val newProgress = ((change.position.x - thumbRadiusPx) / usableWidthPx).coerceIn(0f, 1f)
                            onProgressChange(newProgress)
                        }
                    }
            )

            // 圆形拖拽手柄 (随着 progress 移动)
            val thumbOffsetXPx = (progress.coerceIn(0f, 1f) * usableWidthPx)
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumbOffsetXPx.roundToInt(), 0) }
                    .size(thumbRadiusDp * 2)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.5.dp, thumbColor, CircleShape)
            )
        }
    }
}

/**
 * 预设精选圆形色卡 (Material You 风格)
 */
@Composable
private fun PresetColorCircleItem(
    preset: SeedColorPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = remember(preset.hex) {
        try { Color(("#${preset.hex}").toColorInt()) } catch (_: Throwable) { Color(0xFF0061A4) }
    }
    val isLight = ColorUtils.calculateLuminance(color.toArgb()) > 0.5

    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选中",
                    tint = if (isLight) Color.Black else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Text(
            text = preset.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * 自定义收藏色块
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FavoriteColorChipItem(
    hex: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val color = remember(hex) {
        try { Color(("#$hex").toColorInt()) } catch (_: Throwable) { Color(0xFF0061A4) }
    }
    val isLight = ColorUtils.calculateLuminance(color.toArgb()) > 0.5

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = onRemove
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已选中",
                        tint = if (isLight) Color.Black else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "#${hex.uppercase()}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
