package com.example.lxmusic.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.sp

import com.example.lxmusic.data.LiquidGlassSettings
import com.example.lxmusic.ui.effect.LiquidGlassConfig
import com.example.lxmusic.ui.effect.LiquidGlassIntensity
import com.example.lxmusic.ui.effect.LiquidGlassPreset

/**
 * 液态玻璃设置界面
 */
@Composable
fun LiquidGlassSettingsScreen(
    settings: LiquidGlassSettings,
    onBack: () -> Unit
) {
    val currentConfig by remember { mutableStateOf(settings.getCurrentConfig()) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            LiquidGlassSettingsTopBar(onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 开关设置
            item {
                SwitchSetting(
                    title = "启用液态玻璃效果",
                    description = "为底部导航栏添加液态玻璃视觉效果",
                    value = settings._enabled,
                    onValueChange = { settings.saveEnabled(it) }
                )
            }
            
            // 预设选择
            if (settings._enabled) {
                item {
                    PresetSelection(
                        preset = settings._preset,
                        onPresetSelected = { preset ->
                            settings.applyPreset(preset)
                        }
                    )
                }
                
                // 强度选择
                item {
                    IntensitySelection(
                        intensity = settings._intensity,
                        onIntensitySelected = { intensity ->
                            settings.saveIntensity(intensity)
                        }
                    )
                }
                
                // 高级设置
                item {
                    AdvancedSettingsSection(settings = settings)
                }
            }
            
            // 预览区域
            item {
                LiquidGlassPreview(
                    config = currentConfig,
                    enabled = settings._enabled
                )
            }
        }
    }
}

/**
 * 液态玻璃设置顶部栏
 */
@Composable
private fun LiquidGlassSettingsTopBar(
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 返回按钮
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // 标题
            Text(
                text = "液态玻璃设置",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 开关设置项
 */
@Composable
private fun SwitchSetting(
    title: String,
    description: String,
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onValueChange(!value) }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Switch(
                checked = value,
                onCheckedChange = onValueChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * 预设选择
 */
@Composable
private fun PresetSelection(
    preset: LiquidGlassPreset,
    onPresetSelected: (LiquidGlassPreset) -> Unit
) {
    Column {
        Text(
            text = "效果预设",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LiquidGlassPreset.values().forEach { presetOption ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp)
                        .clickable { onPresetSelected(presetOption) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (preset == presetOption) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surface
                    ),
                    border = if (preset == presetOption) 
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    else 
                        null
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = when (presetOption) {
                                LiquidGlassPreset.CLEAR -> Icons.Default.BubbleChart
                                LiquidGlassPreset.BALANCED -> Icons.Default.Palette
                                LiquidGlassPreset.FROSTED -> Icons.Default.Settings
                                LiquidGlassPreset.DISABLED -> Icons.Default.Tune
                            },
                            contentDescription = presetOption.name,
                            tint = if (preset == presetOption) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = presetOption.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preset == presetOption) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 强度选择
 */
@Composable
private fun IntensitySelection(
    intensity: LiquidGlassIntensity,
    onIntensitySelected: (LiquidGlassIntensity) -> Unit
) {
    Column {
        Text(
            text = "效果强度",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LiquidGlassIntensity.values().forEach { intensityOption ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onIntensitySelected(intensityOption) }
                ) {
                    RadioButton(
                        selected = intensity == intensityOption,
                        onClick = { onIntensitySelected(intensityOption) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = intensityOption.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * 高级设置
 */
@Composable
private fun AdvancedSettingsSection(
    settings: LiquidGlassSettings
) {
    Column {
        Text(
            text = "高级设置",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 折射强度
        SliderSetting(
            title = "折射强度",
            value = settings._refractIntensity,
            onValueChange = { settings.saveRefractIntensity(it) },
            valueRange = 0f..0.5f,
            steps = 50
        )
        
        // 色差强度
        SliderSetting(
            title = "色差强度",
            value = settings._chromaticAberration,
            onValueChange = { settings.saveChromaticAberration(it) },
            valueRange = 0f..1f,
            steps = 100
        )
        
        // 圆角半径
        SliderSetting(
            title = "圆角半径",
            value = settings._cornerRadius,
            onValueChange = { settings.saveCornerRadius(it) },
            valueRange = 16f..64f,
            steps = 48
        )
    }
}

/**
 * 滑块设置项
 */
@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "%.2f".format(value),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )
    }
}

/**
 * 液态玻璃预览
 */
@Composable
private fun LiquidGlassPreview(
    config: LiquidGlassConfig,
    enabled: Boolean
) {
    Column {
        Text(
            text = "效果预览",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (enabled && config.enabled) {
                    // 这里可以添加预览效果
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(config.cornerRadius))
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                    )
                } else {
                    Text(
                        text = "液态玻璃效果已关闭",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}