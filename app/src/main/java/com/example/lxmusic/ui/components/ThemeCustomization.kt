package com.example.lxmusic.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.lxmusic.ui.components.wallpaper.WallpaperCropDialog
import java.io.File

/**
 * 壁纸条目
 */
data class WallpaperItem(
    val id: String,
    val filePath: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 加载所有已保存的壁纸
 */
fun loadSavedWallpapers(context: Context): List<WallpaperItem> {
    val dir = File(context.filesDir, "wallpapers")
    if (!dir.exists()) return emptyList()
    return dir.listFiles { file -> file.isFile && (file.name.endsWith(".jpg") || file.name.endsWith(".png") || file.name.endsWith(".jpeg")) }
        ?.sortedByDescending { it.lastModified() }
        ?.map { file ->
            WallpaperItem(
                id = file.name,
                filePath = file.absolutePath,
                timestamp = file.lastModified()
            )
        } ?: emptyList()
}

/**
 * 重构后的「自定义个性化 - 手机标准壁纸相册」管理组件
 */
@Composable
fun ThemeCustomizationSection(
    presets: List<Any> = emptyList(),
    isExpanded: Boolean = true,
    onExpandedChange: (Boolean) -> Unit = {},
    onAddPreset: (Any, Boolean) -> Unit = { _, _ -> },
    onDeletePreset: (String) -> Unit = {},
    onApplyPreset: (Any) -> Unit = {},
    onEditPreset: (Any, Boolean) -> Unit = { _, _ -> },
    appliedPresetId: String? = null,
    themeMode: String = "dynamic",
    currentBgImageUri: String? = null,
    hasBackgroundImage: Boolean = false,
    bgOpacity: Float = 0.5f,
    onOpacityChange: (Float) -> Unit = {},
    onResetDefaults: () -> Unit = {},
    onClearBackgroundImage: () -> Unit = {},
    onRegisterPickAction: (() -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val settingsPrefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    
    // 当前应用的壁纸 ID 跟踪
    var appliedWallpaperId by remember {
        mutableStateOf(settingsPrefs.getString("applied_wallpaper_id", null))
    }
    
    var wallpapers by remember { mutableStateOf(loadSavedWallpapers(context)) }
    var selectedCropUri by remember { mutableStateOf<Uri?>(null) }
    var wallpaperToDelete by remember { mutableStateOf<WallpaperItem?>(null) }

    // 系统相册选择器
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedCropUri = uri
        }
    }

    // 注册相册选择动作给外层悬浮按键
    val triggerPickAction = remember {
        {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
    LaunchedEffect(triggerPickAction) {
        onRegisterPickAction(triggerPickAction)
    }

    // 裁剪对话框
    selectedCropUri?.let { uri ->
        WallpaperCropDialog(
            imageUri = uri,
            onDismiss = { selectedCropUri = null },
            onCropSuccess = { croppedFile ->
                selectedCropUri = null
                wallpapers = loadSavedWallpapers(context)
                val newId = croppedFile.name
                appliedWallpaperId = newId
                settingsPrefs.edit().putString("applied_wallpaper_id", newId).apply()
                onAddPreset(croppedFile.absolutePath, true)
            }
        )
    }

    // 清除壁纸操作
    val handleClearWallpaper: () -> Unit = {
        appliedWallpaperId = null
        settingsPrefs.edit().remove("applied_wallpaper_id").apply()
        onClearBackgroundImage()
    }

    // 应用壁纸操作
    val handleApplyWallpaper: (WallpaperItem) -> Unit = { wallpaper ->
        appliedWallpaperId = wallpaper.id
        settingsPrefs.edit().putString("applied_wallpaper_id", wallpaper.id).apply()
        onAddPreset(wallpaper.filePath, true)
    }

    // 删除确认对话框
    wallpaperToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { wallpaperToDelete = null },
            title = { Text("删除壁纸", fontWeight = FontWeight.Bold) },
            text = { Text("确定要从壁纸库中移除此壁纸吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = File(item.filePath)
                        if (file.exists()) file.delete()
                        wallpapers = loadSavedWallpapers(context)
                        // 如果删除的是当前应用的壁纸，取消壁纸
                        if (appliedWallpaperId == item.id) {
                            handleClearWallpaper()
                        }
                        wallpaperToDelete = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { wallpaperToDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ===== 1. 壁纸库标题与快捷取消操作 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Wallpaper,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "手机壁纸库",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (currentBgImageUri != null) {
                Surface(
                    onClick = handleClearWallpaper,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "取消当前壁纸",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // ===== 2. 不透明度调节卡片（当应用了壁纸时展示） =====
        AnimatedVisibility(
            visible = currentBgImageUri != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "壁纸不透明度",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${(bgOpacity * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = bgOpacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.1f..1.0f,
                        steps = 18,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        // ===== 3. 3 列标准手机屏幕比例壁纸网格 =====
        // 标准高宽比：9 : 19.5 (0.4615)
        val cardAspectRatio = 9f / 19.5f

        val totalItems = 1 + wallpapers.size
        val rows = (totalItems + 2) / 3

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (rowIndex in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (colIndex in 0 until 3) {
                        val itemIndex = rowIndex * 3 + colIndex
                        if (itemIndex < totalItems) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (itemIndex == 0) {
                                    // 第 0 项：默认无壁纸（纯色主题）
                                    // 仅当当前没有壁纸或 appliedWallpaperId 为空时选中
                                    val isSelected = currentBgImageUri == null || appliedWallpaperId == null
                                    DefaultNoWallpaperCard(
                                        isSelected = isSelected,
                                        aspectRatio = cardAspectRatio,
                                        onClick = handleClearWallpaper
                                    )
                                } else {
                                    // 第 1..N 项：用户壁纸
                                    val wallpaper = wallpapers[itemIndex - 1]
                                    // 当前有壁纸且 appliedWallpaperId 匹配该壁纸时精准高亮
                                    val isSelected = currentBgImageUri != null && appliedWallpaperId == wallpaper.id
                                    WallpaperCard(
                                        wallpaper = wallpaper,
                                        isSelected = isSelected,
                                        aspectRatio = cardAspectRatio,
                                        onClick = {
                                            handleApplyWallpaper(wallpaper)
                                        },
                                        onLongClick = {
                                            wallpaperToDelete = wallpaper
                                        }
                                    )
                                }
                            }
                        } else {
                            // 占位空白
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // 底部提示
        Text(
            text = "提示：点击壁纸可一键应用，长按壁纸可删除；点击右下角加号选择相册图片进行标准裁剪添加。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/**
 * 默认无壁纸（纯色主题）卡片
 */
@Composable
private fun DefaultNoWallpaperCard(
    isSelected: Boolean,
    aspectRatio: Float,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderModifier = if (isSelected) {
        Modifier.border(3.dp, primaryColor, RoundedCornerShape(16.dp))
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .then(borderModifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = null,
                    tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "纯色主题",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "无壁纸",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                    fontSize = 10.sp
                )
            }

            // 选中对勾标记
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .background(primaryColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已应用",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * 手机壁纸预览卡片
 */
@Composable
private fun WallpaperCard(
    wallpaper: WallpaperItem,
    isSelected: Boolean,
    aspectRatio: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderModifier = if (isSelected) {
        Modifier.border(3.dp, primaryColor, RoundedCornerShape(16.dp))
    } else {
        Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
    }

    val imageModel = remember(wallpaper.filePath) { File(wallpaper.filePath) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(16.dp))
            .then(borderModifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Image(
            painter = rememberAsyncImagePainter(model = imageModel),
            contentDescription = "壁纸预览",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 选中对勾徽章
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(primaryColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已应用",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 兼容旧版的预设模型
 */
data class ThemePreset(
    val id: String = "",
    val name: String = "",
    val backgroundImageUri: String? = null
)

/**
 * 导航栏/底栏透明度高级自定义组件
 */
@Composable
fun NavBarCustomizationSection(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isModernTheme: Boolean,
    navBarOpacity: Float,
    onNavBarOpacityChange: (Float) -> Unit,
    playerBarOpacity: Float,
    onPlayerBarOpacityChange: (Float) -> Unit,
    playerBarWhiteBlend: Float,
    onPlayerBarWhiteBlendChange: (Float) -> Unit,
    floatingBarOpacity: Float,
    onFloatingBarOpacityChange: (Float) -> Unit,
    clickAnimationSpeed: Float,
    onClickAnimationSpeedChange: (Float) -> Unit,
    onResetDefaults: () -> Unit,
    onAutoBalance: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "底栏与播放条透明度",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 悬浮底栏不透明度
            val currentNavOpacity = if (isModernTheme) floatingBarOpacity else navBarOpacity
            Text(
                text = "底栏不透明度: ${(currentNavOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = currentNavOpacity,
                onValueChange = {
                    if (isModernTheme) onFloatingBarOpacityChange(it) else onNavBarOpacityChange(it)
                },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 播放条不透明度
            Text(
                text = "播放条不透明度: ${(playerBarOpacity * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = playerBarOpacity,
                onValueChange = onPlayerBarOpacityChange,
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary)
            )
        }
    }
}
