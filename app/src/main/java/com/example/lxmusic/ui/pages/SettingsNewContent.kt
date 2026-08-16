package com.example.lxmusic.ui.pages

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.lxmusic.data.StorageCacheClearOptions
import com.example.lxmusic.data.StorageCacheKind
import com.example.lxmusic.data.StorageUsageItem
import com.example.lxmusic.data.StorageUsageSummary
import com.example.lxmusic.data.analyzeStorageUsage
import com.example.lxmusic.data.clearStorageCaches
import com.example.lxmusic.data.formatFileSize
import kotlinx.coroutines.launch
import java.io.File

// ==================== 通用设置项 ====================

@Composable
internal fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
internal fun SettingsChoiceItem(
    title: String,
    subtitle: String,
    currentLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== UI 缩放滑块 ====================

@Composable
internal fun SettingsScaleItem(
    title: String,
    description: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ==================== UI 缩放对话框（对齐 NeriPlayer 的 DpiSettingDialog） ====================

@Composable
internal fun UiScaleDialog(
    currentScale: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    var sliderValue by remember(currentScale) { mutableFloatStateOf(currentScale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("UI 缩放") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.2fx", sliderValue),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 0.6f..1.2f,
                    steps = 11,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "调节后点击「应用」生效",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(sliderValue)
                    onDismiss()
                }
            ) { Text("应用") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { sliderValue = 1.0f }
                ) { Text("重置") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

// ==================== 存储与缓存 ====================

@Composable
internal fun StorageCacheSection(
    prefs: android.content.SharedPreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var storageSummary by remember { mutableStateOf(StorageUsageSummary.Empty) }
    var isLoading by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var clearImage by remember { mutableStateOf(true) }
    var clearPlayback by remember { mutableStateOf(true) }
    var clearBackground by remember { mutableStateOf(false) }
    var clearThemeImages by remember { mutableStateOf(false) }

    var maxCacheMb by remember {
        mutableFloatStateOf(
            prefs.getLong("max_cache_size_bytes", 1024L * 1024 * 1024) / (1024f * 1024f)
        )
    }

    fun refresh() {
        isLoading = true
        scope.launch {
            storageSummary = analyzeStorageUsage(context)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 缓存上限
        Text(
            text = "缓存上限",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "最大缓存大小",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "限制网络缓存（图片、临时播放缓存）占用的磁盘空间",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (maxCacheMb < 10f) "不缓存" else cacheSizeLabel(maxCacheMb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Slider(
                    value = maxCacheMb,
                    onValueChange = { maxCacheMb = it },
                    onValueChangeFinished = {
                        val bytes = if (maxCacheMb < 10f) 0L else (maxCacheMb * 1024 * 1024).toLong()
                        prefs.edit().putLong("max_cache_size_bytes", bytes).apply()
                    },
                    valueRange = 0f..(10 * 1024f),
                    steps = 0,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "实际生效以播放器与图片库为准",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 存储详情
        Text(
            text = "存储占用",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                storageSummary.sections.forEach { section ->
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    section.items.forEach { item ->
                        StorageUsageRow(item)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "总占用",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formatFileSize(storageSummary.totalSizeBytes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDetailsDialog = true },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("查看详情")
                    }
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("清除缓存")
                    }
                }
            }
        }
    }

    // 详情对话框
    if (showDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("存储详情") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (isLoading) {
                        Text("正在计算…", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        storageSummary.sections.forEach { section ->
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            section.items.forEach { item ->
                                StorageUsageRow(item)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("总占用", style = MaterialTheme.typography.titleSmall)
                            Text(
                                formatFileSize(storageSummary.totalSizeBytes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailsDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }

    // 清除缓存对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("确认清除缓存") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("选择要清除的缓存类型：", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    CacheTypeRow("图片缓存", storageSummary.sizeOf(StorageCacheKind.Image), clearImage) {
                        clearImage = it
                    }
                    CacheTypeRow("播放缓存", storageSummary.sizeOf(StorageCacheKind.Playback), clearPlayback) {
                        clearPlayback = it
                    }
                    CacheTypeRow(
                        "背景图片",
                        storageSummary.sizeOf(StorageCacheKind.BackgroundImage),
                        clearBackground
                    ) { clearBackground = it }
                    CacheTypeRow(
                        "主题预设图片",
                        storageSummary.sizeOf(StorageCacheKind.ThemeImages),
                        clearThemeImages
                    ) { clearThemeImages = it }
                }
            },
            confirmButton = {
                val options = StorageCacheClearOptions(
                    imageCache = clearImage,
                    playbackCache = clearPlayback,
                    backgroundImage = clearBackground,
                    themeImages = clearThemeImages
                )
                Button(
                    onClick = {
                        showClearDialog = false
                        scope.launch {
                            val result = clearStorageCaches(context, options)
                            refresh()
                            if (result.deletedFiles > 0) {
                                if (clearBackground) {
                                    prefs.edit().remove("bg_image_path").apply()
                                }
                            }
                        }
                    },
                    enabled = options.hasSelection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (options.hasSelection) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) { Text("清除") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun CacheTypeRow(
    title: String,
    sizeBytes: Long,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange(it) }
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                formatFileSize(sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StorageUsageRow(item: StorageUsageItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = item.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatFileSize(item.sizeBytes),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${item.fileCount} 个文件",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun cacheSizeLabel(sizeMb: Float): String = if (sizeMb >= 1024f) {
    String.format("%.1f GB", sizeMb / 1024f)
} else {
    "${sizeMb.toInt()} MB"
}

// ==================== 播放服务空闲退出 ====================

internal val IDLE_SHUTDOWN_OPTIONS = listOf(0, 5, 10, 15, 30, 60)

internal fun idleShutdownLabel(minutes: Int): String = when (minutes) {
    0 -> "关闭"
    5 -> "5 分钟"
    10 -> "10 分钟"
    15 -> "15 分钟"
    30 -> "30 分钟"
    60 -> "60 分钟"
    else -> "$minutes 分钟"
}

@Composable
internal fun IdleShutdownDialog(
    current: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("暂停后退出播放服务") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                IDLE_SHUTDOWN_OPTIONS.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(minutes)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == current,
                            onClick = { onSelect(minutes) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = idleShutdownLabel(minutes),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

