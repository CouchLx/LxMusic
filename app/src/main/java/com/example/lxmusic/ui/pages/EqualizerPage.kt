package com.example.lxmusic.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.lxmusic.audio.eq.EqBand
import com.example.lxmusic.audio.eq.EqPresets
import com.example.lxmusic.audio.eq.EqualizerController
import com.example.lxmusic.audio.eq.LimiterParams
import com.example.lxmusic.audio.eq.CompressorParams
import com.example.lxmusic.ui.components.AudioChip
import com.example.lxmusic.ui.components.AudioParameterSlider
import com.example.lxmusic.ui.components.AudioSettingsCard
import com.example.lxmusic.ui.components.AudioSettingsPageScaffold
import com.example.lxmusic.ui.components.AudioSwitchRow
import com.example.lxmusic.ui.components.AudioValueText
import com.example.lxmusic.ui.components.EqualizerWaveform
import com.example.lxmusic.ui.components.effectPercent
import java.util.Locale

/**
 * 均衡器页面（移植自 BoomingMusic 的 EqualizerScreen）：
 * 总开关 → 预设/段数 → 图示均衡（响应曲线 + 每段滑杆）→ 音效增强 → 专业模式（压缩/限制）。
 * 所有改动实时作用于播放会话，并持久化到 equalizer prefs。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerPage(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by EqualizerController.state.collectAsState()
    var showPresetSheet by remember { mutableStateOf(false) }

    // 进入页面刷新一次输出约束（高解析 / USB 独占会绕过系统音效）
    LaunchedEffect(Unit) { EqualizerController.refreshOutputConstraints() }

    // 滑杆本地镜像：拖动时先本地跟手，松手落盘；预设/段数变化时整体同步
    val bandLevels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(state.bandCount, state.presetName, state.bands.size) {
        bandLevels.clear()
        bandLevels.addAll(state.bands.map { it.gainDb })
    }

    AudioSettingsPageScaffold(
        title = "均衡器",
        onBack = onBack,
        actions = {
            IconButton(onClick = { EqualizerController.resetAll() }) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "全部重置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== 总开关 =====
            item {
                AudioSettingsCard(
                    title = "均衡器",
                    icon = Icons.Default.Equalizer,
                    trailing = {
                        AudioChip(
                            text = state.engineLabel,
                            selected = state.active,
                            enabled = false
                        )
                    }
                ) {
                    AudioSwitchRow(
                        title = "启用均衡器",
                        subtitle = when {
                            !state.supported -> "当前设备不支持系统音效"
                            !state.enabled -> "开启后立即作用于当前播放"
                            !state.active -> "开始播放后自动挂载到当前音频会话"
                            else -> "实时作用于当前播放"
                        },
                        checked = state.enabled,
                        enabled = state.supported,
                        onCheckedChange = { EqualizerController.setEnabled(it) }
                    )
                    state.notice?.let { notice ->
                        Text(
                            text = notice,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ===== 预设 + 图示均衡 =====
            item {
                AudioSettingsCard(
                    title = "预设与频段",
                    icon = Icons.Default.GraphicEq,
                    trailing = {
                        TextButton(
                            onClick = { showPresetSheet = true },
                            enabled = state.supported
                        ) {
                            Text(
                                text = state.presetName,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                ) {
                    if (state.availableBandCounts.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            state.availableBandCounts.forEach { count ->
                                AudioChip(
                                    text = "$count 段",
                                    selected = count == state.bandCount,
                                    enabled = state.isUsable,
                                    onClick = { EqualizerController.setBandCount(count) }
                                )
                            }
                        }
                    }

                    EqualizerWaveform(
                        bands = bandLevels.toList(),
                        valueRange = state.bandRange,
                        enabled = state.isUsable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .padding(vertical = 8.dp)
                    )

                    if (state.bands.isEmpty()) {
                        Text(
                            text = "当前引擎没有可调频段",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    } else {
                        state.bands.forEachIndexed { index, band ->
                            EqualizerBandSlider(
                                band = band,
                                level = bandLevels.getOrElse(index) { band.gainDb },
                                enabled = state.isUsable,
                                onValueChange = { value ->
                                    if (index < bandLevels.size) bandLevels[index] = value
                                    EqualizerController.setBandGain(index, value)
                                },
                                onValueChangeFinished = { EqualizerController.commitLevels() }
                            )
                        }
                    }
                }
            }

            // ===== 音效增强（按设备支持情况显示） =====
            if (state.bassBoost.supported) {
                item {
                    AudioSettingsCard(title = "低音增强", icon = Icons.Default.VolumeUp) {
                        AudioSwitchRow(
                            title = "低音增强",
                            subtitle = "强化低频下潜与力度",
                            checked = state.bassBoost.enabled && state.enabled,
                            enabled = state.isUsable,
                            onCheckedChange = { EqualizerController.setBassBoost(enabled = it) }
                        )
                        SharedEffectSlider(
                            value = state.bassBoost.value,
                            range = state.bassBoost.range,
                            enabled = state.isUsable && state.bassBoost.enabled,
                            onValueChangeFinished = {
                                EqualizerController.setBassBoost(strength = it)
                            }
                        )
                    }
                }
            }

            if (state.virtualizer.supported) {
                item {
                    AudioSettingsCard(title = "环绕声", icon = Icons.Default.SurroundSound) {
                        AudioSwitchRow(
                            title = "环绕声",
                            subtitle = "虚拟环绕，扩展声场（耳机效果更明显）",
                            checked = state.virtualizer.enabled && state.enabled,
                            enabled = state.isUsable,
                            onCheckedChange = { EqualizerController.setVirtualizer(enabled = it) }
                        )
                        SharedEffectSlider(
                            value = state.virtualizer.value,
                            range = state.virtualizer.range,
                            enabled = state.isUsable && state.virtualizer.enabled,
                            onValueChangeFinished = {
                                EqualizerController.setVirtualizer(strength = it)
                            }
                        )
                    }
                }
            }

            if (state.loudness.supported) {
                item {
                    AudioSettingsCard(title = "响度增强", icon = Icons.Default.Headphones) {
                        AudioSwitchRow(
                            title = "响度增强",
                            subtitle = "整体提升音量，适合小音量环境收听",
                            checked = state.loudness.enabled && state.enabled,
                            enabled = state.isUsable,
                            onCheckedChange = { EqualizerController.setLoudnessGain(enabled = it) }
                        )
                        SharedEffectSlider(
                            value = state.loudness.value,
                            range = state.loudness.range,
                            enabled = state.isUsable && state.loudness.enabled,
                            valueFormatter = { "${it.toInt()} dB" },
                            onValueChangeFinished = {
                                EqualizerController.setLoudnessGain(gainDb = it)
                            }
                        )
                    }
                }
            }

            // ===== 专业模式 =====
            item {
                AudioSettingsCard(title = "专业模式", icon = Icons.Default.Tune) {
                    AudioSwitchRow(
                        title = "开启专业模式",
                        subtitle = if (state.proSupported) {
                            "解锁多段压缩器与限制器"
                        } else {
                            "当前引擎不支持（需要 Android 9 及以上）"
                        },
                        checked = state.proMode,
                        enabled = state.isUsable && state.proSupported,
                        onCheckedChange = { EqualizerController.setProMode(it) }
                    )
                }
            }

            if (state.proMode && state.proSupported) {
                item {
                    CompressorCard(
                        params = state.compressor,
                        enabled = state.isUsable,
                        onToggle = { EqualizerController.setCompressor(enabled = it) }
                    )
                }
                item {
                    LimiterCard(
                        params = state.limiter,
                        enabled = state.isUsable,
                        onToggle = { EqualizerController.setLimiter(enabled = it) }
                    )
                }
            }
        }
    }

    if (showPresetSheet) {
        PresetSelectorSheet(
            currentPreset = state.presetName,
            bandCount = state.bandCount,
            onSelect = {
                EqualizerController.applyPreset(it)
                showPresetSheet = false
            },
            onDismiss = { showPresetSheet = false }
        )
    }
}

@Composable
private fun EqualizerBandSlider(
    band: EqBand,
    level: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = band.readableFrequency,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(56.dp)
        )
        Slider(
            value = level,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = band.range,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        AudioValueText(text = String.format(Locale.ROOT, "%+.1f dB", level))
    }
}

@Composable
private fun SharedEffectSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    valueFormatter: (Float) -> String = { effectPercent(it, range) },
    onValueChangeFinished: (Float) -> Unit
) {
    var localValue by remember(value) {
        androidx.compose.runtime.mutableFloatStateOf(value)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValueChangeFinished(localValue) },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        AudioValueText(text = valueFormatter(localValue))
    }
}

@Composable
private fun CompressorCard(
    params: CompressorParams,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    AudioSettingsCard(title = "压缩器", icon = Icons.Default.Equalizer, trailing = {
        Switch(
            checked = params.enabled,
            enabled = enabled,
            onCheckedChange = onToggle
        )
    }) {
        if (!params.enabled) {
            Text(
                text = "开启后压缩动态范围，避免大音量爆音、小音量听不清",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AudioParameterSlider(
                label = "阈值", value = params.threshold,
                range = CompressorParams.THRESHOLD_RANGE, unit = " dB", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(threshold = it) }
            )
            AudioParameterSlider(
                label = "压缩比", value = params.ratio,
                range = CompressorParams.RATIO_RANGE, format = "%.1f:1", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(ratio = it) }
            )
            AudioParameterSlider(
                label = "启动时间", value = params.attackTimeMs,
                range = CompressorParams.ATTACK_RANGE, unit = " ms", format = "%.0f", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(attackTimeMs = it) }
            )
            AudioParameterSlider(
                label = "释放时间", value = params.releaseTimeMs,
                range = CompressorParams.RELEASE_RANGE, unit = " ms", format = "%.0f", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(releaseTimeMs = it) }
            )
            AudioParameterSlider(
                label = "拐点宽度", value = params.kneeWidth,
                range = CompressorParams.KNEE_RANGE, unit = " dB", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(kneeWidth = it) }
            )
            AudioParameterSlider(
                label = "噪声门", value = params.noiseGateThreshold,
                range = CompressorParams.NOISE_GATE_RANGE, unit = " dB", format = "%.0f", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(noiseGateThreshold = it) }
            )
            AudioParameterSlider(
                label = "前置增益", value = params.preGain,
                range = CompressorParams.GAIN_RANGE, unit = " dB", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(preGain = it) }
            )
            AudioParameterSlider(
                label = "后置增益", value = params.postGain,
                range = CompressorParams.GAIN_RANGE, unit = " dB", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setCompressor(postGain = it) }
            )
        }
    }
}

@Composable
private fun LimiterCard(
    params: LimiterParams,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    AudioSettingsCard(title = "限制器", icon = Icons.Default.SurroundSound, trailing = {
        Switch(
            checked = params.enabled,
            enabled = enabled,
            onCheckedChange = onToggle
        )
    }) {
        if (!params.enabled) {
            Text(
                text = "开启后限制峰值电平，防止削波失真",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            AudioParameterSlider(
                label = "阈值", value = params.threshold,
                range = LimiterParams.THRESHOLD_RANGE, unit = " dB", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setLimiter(threshold = it) }
            )
            AudioParameterSlider(
                label = "压缩比", value = params.ratio,
                range = LimiterParams.RATIO_RANGE, format = "%.1f:1", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setLimiter(ratio = it) }
            )
            AudioParameterSlider(
                label = "启动时间", value = params.attackTimeMs,
                range = LimiterParams.ATTACK_RANGE, unit = " ms", format = "%.0f", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setLimiter(attackTimeMs = it) }
            )
            AudioParameterSlider(
                label = "释放时间", value = params.releaseTimeMs,
                range = LimiterParams.RELEASE_RANGE, unit = " ms", format = "%.0f", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setLimiter(releaseTimeMs = it) }
            )
            AudioParameterSlider(
                label = "后置增益", value = params.postGain,
                range = LimiterParams.GAIN_RANGE, unit = " dB", enabled = enabled,
                onValueChangeFinished = { EqualizerController.setLimiter(postGain = it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSelectorSheet(
    currentPreset: String,
    bandCount: Int,
    onSelect: (com.example.lxmusic.audio.eq.EqPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val presets = remember(bandCount) { EqPresets.builtIn(bandCount) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "选择预设",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(presets, key = { it.name }) { preset ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = preset.name,
                                fontWeight = if (preset.name == currentPreset) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "$bandCount 段图示均衡",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (preset.name == currentPreset) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        ),
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clickable { onSelect(preset) }
                    )
                }
            }
        }
    }
}
