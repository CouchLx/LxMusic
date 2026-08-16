package com.example.lxmusic.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import java.io.File
import java.util.UUID

// 主题预设数据模型（仅保存背景图，不再携带自定义颜色）
data class ThemePreset(
    val id: String,
    val name: String,
    val backgroundImageUri: String?
)

/**
 * 将content:// URI的图片复制到应用内部存储
 * 返回内部存储的file://路径
 */
fun copyImageToInternalStorage(context: Context, sourceUri: String): String? {
    return try {
        val uri = Uri.parse(sourceUri)
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        
        // 创建内部存储目录
        val imagesDir = File(context.filesDir, "theme_images")
        if (!imagesDir.exists()) imagesDir.mkdirs()
        
        // 生成唯一文件名
        val fileName = "theme_${UUID.randomUUID()}.jpg"
        val outputFile = File(imagesDir, fileName)
        
        // 复制文件
        inputStream.use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        
        // 返回内部存储路径
        outputFile.absolutePath
    } catch (e: Exception) {
        android.util.Log.e("LxMusic", "copyImageToInternalStorage failed", e)
        null
    }
}

/**
 * 主题自定义组件 - 可折叠
 */
@Composable
fun ThemeCustomizationSection(
    presets: List<ThemePreset>,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddPreset: (ThemePreset, Boolean) -> Unit,       // (preset, applyNow)
    onDeletePreset: (String) -> Unit,
    onApplyPreset: (ThemePreset) -> Unit,
    onEditPreset: (ThemePreset, Boolean) -> Unit,       // (preset, applyNow)
    appliedPresetId: String? = null,
    themeMode: String = "dynamic",
    currentBgImageUri: String? = null,
    hasBackgroundImage: Boolean = false,
    bgOpacity: Float = 0.5f,
    onOpacityChange: (Float) -> Unit = {},
    onResetDefaults: () -> Unit = {},
    onClearBackgroundImage: () -> Unit = {}
) {
    android.util.Log.d("LxMusic", "ThemeCustomizationSection: START render, presets=${presets.size}, expanded=$isExpanded")
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPreset by remember { mutableStateOf<ThemePreset?>(null) }
    var deletingPreset by remember { mutableStateOf<ThemePreset?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏 - 无点击特效
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandedChange(!isExpanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "主题自定义",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开"
                )
            }

            // 展开内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // 添加按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加预设")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 预设列表
                    if (presets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无预设，点击上方按钮添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { preset ->
                                // 判断预设是否已应用：只看预设ID是否匹配
                                val isApplied = appliedPresetId == preset.id
                                ThemePresetCard(
                                    preset = preset,
                                    isApplied = isApplied,
                                    onApply = { onApplyPreset(preset) },
                                    onEdit = { editingPreset = preset },
                                    onDelete = { deletingPreset = preset }
                                )
                            }
                        }
                    }

                    // 背景不透明度滑块（仅在有背景图片时显示）
                    if (hasBackgroundImage) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "背景不透明度: ${(bgOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = bgOpacity,
                            onValueChange = onOpacityChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // --- 恢复默认按钮区域 ---
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 恢复默认预设按钮
                        OutlinedButton(
                            onClick = onResetDefaults,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("恢复默认预设", style = MaterialTheme.typography.bodySmall)
                        }
                        
                        // 仅清除背景图片按钮
                        if (hasBackgroundImage) {
                            OutlinedButton(
                                onClick = onClearBackgroundImage,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("清除背景图片", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // 添加预设对话框
    if (showAddDialog) {
        AddThemePresetDialog(
            onDismiss = { showAddDialog = false },
            onSave = { preset ->
                onAddPreset(preset, false)
                showAddDialog = false
            },
            onSaveAndApply = { preset ->
                onAddPreset(preset, true)
                showAddDialog = false
            }
        )
    }

    // 编辑预设对话框
    editingPreset?.let { preset ->
        EditThemePresetDialog(
            preset = preset,
            onDismiss = { editingPreset = null },
            onSave = { editedPreset ->
                onEditPreset(editedPreset, false)
                editingPreset = null
            },
            onSaveAndApply = { editedPreset ->
                onEditPreset(editedPreset, true)
                editingPreset = null
            }
        )
    }

    // 删除确认弹窗
    deletingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = { deletingPreset = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            title = { Text("确认删除") },
            text = { Text("确定要删除预设「${preset.name}」吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePreset(preset.id)
                        deletingPreset = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deletingPreset = null }) { Text("取消") }
            }
        )
    }
}

/**
 * 主题预设卡片
 */
@Composable
fun ThemePresetCard(
    preset: ThemePreset,
    isApplied: Boolean = false,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 背景图片预览 - 竖屏比例 9:16
            Box(
                modifier = Modifier
                    .height(72.dp)
                    .aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (preset.backgroundImageUri != null) {
                    // 支持内部存储绝对路径和content:// URI
                    val imageModel: Any = if (preset.backgroundImageUri.startsWith("/")) {
                        File(preset.backgroundImageUri)
                    } else {
                        Uri.parse(preset.backgroundImageUri)
                    }
                    val painter = rememberAsyncImagePainter(model = imageModel)
                    Image(
                        painter = painter,
                        contentDescription = "背景预览",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 名称
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // 编辑按钮
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 删除按钮
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 应用按钮
            if (isApplied) {
                // 已应用状态
                Button(
                    onClick = {},
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("已应用")
                }
            } else {
                // 未应用状态
                Button(
                    onClick = onApply,
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                ) {
                    Text("应用")
                }
            }
        }
    }
}

/**
 * 添加主题预设对话框 - 保存 / 保存并应用 双按钮
 */
@Composable
fun AddThemePresetDialog(
    onDismiss: () -> Unit,
    onSave: (ThemePreset) -> Unit,
    onSaveAndApply: (ThemePreset) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.toString()?.let { contentUri ->
            // 将content:// URI复制到内部存储，确保重启后仍可访问
            val internalPath = copyImageToInternalStorage(context, contentUri)
            selectedImageUri = internalPath ?: contentUri
        }
    }

    val buildPreset: () -> ThemePreset? = {
        if (presetName.isNotBlank()) {
            ThemePreset(
                id = System.currentTimeMillis().toString(),
                name = presetName,
                backgroundImageUri = selectedImageUri
            )
        } else null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("添加主题预设") },
        text = {
            DialogContent(
                presetName = presetName,
                onPresetNameChange = { presetName = it },
                selectedImageUri = selectedImageUri,
                onImagePick = { imagePickerLauncher.launch("image/*") }
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { buildPreset()?.let { onSave(it) } },
                    enabled = presetName.isNotBlank()
                ) { Text("保存") }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { buildPreset()?.let { onSaveAndApply(it) } },
                    enabled = presetName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("保存并应用") }
            }
        }
    )
}

/**
 * 编辑主题预设对话框 - 保存 / 保存并应用 双按钮
 */
@Composable
fun EditThemePresetDialog(
    preset: ThemePreset,
    onDismiss: () -> Unit,
    onSave: (ThemePreset) -> Unit,
    onSaveAndApply: (ThemePreset) -> Unit
) {
    var presetName by remember { mutableStateOf(preset.name) }
    var selectedImageUri by remember { mutableStateOf(preset.backgroundImageUri) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.toString()?.let { contentUri ->
            // 将content:// URI复制到内部存储，确保重启后仍可访问
            val internalPath = copyImageToInternalStorage(context, contentUri)
            selectedImageUri = internalPath ?: contentUri
        }
    }

    val buildPreset: () -> ThemePreset? = {
        if (presetName.isNotBlank()) {
            preset.copy(
                name = presetName,
                backgroundImageUri = selectedImageUri
            )
        } else null
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("编辑主题预设") },
        text = {
            DialogContent(
                presetName = presetName,
                onPresetNameChange = { presetName = it },
                selectedImageUri = selectedImageUri,
                onImagePick = { imagePickerLauncher.launch("image/*") }
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { buildPreset()?.let { onSave(it) } },
                    enabled = presetName.isNotBlank()
                ) { Text("保存") }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { buildPreset()?.let { onSaveAndApply(it) } },
                    enabled = presetName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) { Text("保存并应用") }
            }
        }
    )
}

/**
 * 对话框共用内容
 */
@Composable
private fun DialogContent(
    presetName: String,
    onPresetNameChange: (String) -> Unit,
    selectedImageUri: String?,
    onImagePick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // 名称输入
        OutlinedTextField(
            value = presetName,
            onValueChange = onPresetNameChange,
            label = { Text("预设名称") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 图片预览框
        Text(
            text = "背景图片（可选）",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onImagePick() },
            contentAlignment = Alignment.Center
        ) {
            if (selectedImageUri != null) {
                // 支持内部存储绝对路径和content:// URI
                val imageModel: Any = if (selectedImageUri.startsWith("/")) {
                    File(selectedImageUri)
                } else {
                    Uri.parse(selectedImageUri)
                }
                val painter = rememberAsyncImagePainter(model = imageModel)
                Image(
                    painter = painter,
                    contentDescription = "预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "选择图片",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "点击选择图片（可选）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 导航栏自定义组件 - 可折叠
 */
@Composable
fun NavBarCustomizationSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isModernTheme: Boolean = false,
    navBarOpacity: Float,
    onNavBarOpacityChange: (Float) -> Unit,
    playerBarOpacity: Float = 1f,
    onPlayerBarOpacityChange: (Float) -> Unit = {},
    playerBarWhiteBlend: Float = 0.8f,
    onPlayerBarWhiteBlendChange: (Float) -> Unit = {},
    floatingBarOpacity: Float = 1f,
    onFloatingBarOpacityChange: (Float) -> Unit = {},
    clickAnimationSpeed: Float = 700f,
    onClickAnimationSpeedChange: (Float) -> Unit = {},
    onResetDefaults: () -> Unit = {},
    onAutoBalance: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题栏 - 无点击特效
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onExpandedChange(!isExpanded) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ViewHeadline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "导航栏自定义",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开"
                )
            }

            // 展开内容
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // --- 顶部按钮 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onResetDefaults,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("还原默认", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onAutoBalance,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("自动平衡", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- 导航栏颜色调节（仅原生主题显示）---
                    if (!isModernTheme) {
                        Text(
                            text = "导航栏颜色调节",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 不透明度调节
                        Text(
                            text = "不透明度: ${(navBarOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = navBarOpacity,
                            onValueChange = onNavBarOpacityChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // --- 播放条透明度调节（与导航栏调节一致：100% 纯色，0% 全透明）---
                        Text(
                            text = "播放条透明度: ${(playerBarOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "100% 为纯色，0% 为全透明",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = playerBarOpacity,
                            onValueChange = onPlayerBarOpacityChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // --- 隔离条 ---
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // --- 音乐播放条颜色调节（仅现代化主题显示；原生主题播放条为 Neri 风格）---
                    if (isModernTheme) {
                        Text(
                            text = "音乐播放条颜色调节",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // 白色混合调节
                        Text(
                            text = "白色混合: ${(playerBarWhiteBlend * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "叠加白色使播放条颜色更柔和",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = playerBarWhiteBlend,
                            onValueChange = onPlayerBarWhiteBlendChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // --- 隔离条 ---
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // --- 悬浮底栏颜色调节（仅现代化主题显示）---
                    if (isModernTheme) {
                        Text(
                            text = "悬浮底栏颜色调节",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "不透明度: ${(floatingBarOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = floatingBarOpacity,
                            onValueChange = onFloatingBarOpacityChange,
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // --- 隔离条 ---
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(12.dp))

                        // --- 指示器点击切换动画速率（仅现代化主题显示）---
                        Text(
                            text = "指示器切换动画速率",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "当前: ${clickAnimationSpeed.toInt()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "数值越大切换越快：260（最慢）~ 1000（最快），默认 700",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = clickAnimationSpeed,
                            onValueChange = onClickAnimationSpeedChange,
                            valueRange = 260f..1000f,
                            steps = 73, // (1000-260)/10 - 1 ≈ 73，每步约 10
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
