package com.example.lxmusic.ui.pages

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Format
import com.example.lxmusic.MAX_SOUND_SPEED
import com.example.lxmusic.MIN_SOUND_SPEED
import com.example.lxmusic.PlayerViewModel
import com.example.lxmusic.ui.components.AudioChip
import com.example.lxmusic.ui.components.AudioSettingsCard
import com.example.lxmusic.ui.components.AudioSettingsPageScaffold
import com.example.lxmusic.ui.components.AudioSwitchRow
import com.example.lxmusic.ui.components.AudioValueText
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 声音设置页面（移植自 BoomingMusic 的 SoundSettingsSheet 下半区）：
 * 输出设备信息 → 音量/声道平衡 → 速度与音调 → 高级输出开关。
 *
 * 音量、变速变调、跳过静音的状态由 PlayerViewModel 持有（与播放服务共用 prefs），
 * 响度均衡 / 高解析输出与设置页共用同一组回调，保证两处状态一致。
 */
@Composable
fun SoundSettingsPage(
    playerViewModel: PlayerViewModel,
    audioFormat: Format?,
    balance: Float,
    onBalanceChange: (Float) -> Unit,
    normalization: Boolean,
    onNormalizationChange: (Boolean) -> Unit,
    highRes: Boolean,
    onHighResChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sound by playerViewModel.soundState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val device = remember(context) { detectOutputDevice(context) }

    AudioSettingsPageScaffold(
        title = "声音设置",
        onBack = onBack,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ===== 输出设备 =====
            item {
                AudioSettingsCard(title = "输出", icon = device.icon) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = device.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "正在通过",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = device.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatAudioDetail(audioFormat),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ===== 音量 + 声道平衡 =====
            item {
                AudioSettingsCard(
                    title = "音量",
                    icon = Icons.Default.VolumeUp,
                    trailing = {
                        IconButton(onClick = {
                            playerViewModel.setVolume(1f)
                            onBalanceChange(0f)
                        }) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "重置音量与声道平衡",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                ) {
                    // 音量滑杆：图标随音量档位切换
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val volumeIcon = when {
                            sound.volume > 0.5f -> Icons.Default.VolumeUp
                            sound.volume > 0.05f -> Icons.Default.VolumeDown
                            else -> Icons.Default.VolumeMute
                        }
                        Icon(
                            imageVector = volumeIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = sound.volume,
                            onValueChange = { playerViewModel.setVolume(it) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        AudioValueText(text = "${(sound.volume * 100).roundToInt()}%")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // 声道平衡（-1 全左 / +1 全右）
                    var localBalance by remember(balance) { mutableFloatStateOf(balance) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "左",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = localBalance,
                            onValueChange = { localBalance = it },
                            onValueChangeFinished = { onBalanceChange(localBalance) },
                            valueRange = -1f..1f,
                            steps = 19,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "右",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = when {
                            localBalance == 0f -> "声道平衡：居中"
                            localBalance < 0f -> "声道平衡：偏左 ${(-localBalance * 100).roundToInt()}%"
                            else -> "声道平衡：偏右 ${(localBalance * 100).roundToInt()}%"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ===== 速度与音调 =====
            item {
                AudioSettingsCard(
                    title = "速度与音调",
                    icon = Icons.Default.Speed,
                    trailing = {
                        AudioChip(
                            text = if (sound.fixedPitch) "音调跟随速度" else "独立变调",
                            selected = !sound.fixedPitch,
                            onClick = { playerViewModel.setFixedPitch(!sound.fixedPitch) }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = { playerViewModel.resetSpeedAndPitch() }) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "重置速度与音调",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                ) {
                    var localSpeed by remember(sound.speed) { mutableFloatStateOf(sound.speed) }
                    var localPitch by remember(sound.displayPitch) {
                        mutableFloatStateOf(sound.displayPitch)
                    }
                    // 播放速度
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = localSpeed,
                            onValueChange = { localSpeed = it },
                            onValueChangeFinished = { playerViewModel.setSpeed(localSpeed) },
                            valueRange = MIN_SOUND_SPEED..MAX_SOUND_SPEED,
                            modifier = Modifier.weight(1f)
                        )
                        AudioValueText(text = String.format(Locale.ROOT, "%.2fx", localSpeed))
                    }
                    // 音调（锁定跟随速度时禁用）
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Slider(
                            value = if (sound.fixedPitch) sound.speed else localPitch,
                            onValueChange = { localPitch = it },
                            onValueChangeFinished = { playerViewModel.setPitch(localPitch) },
                            valueRange = MIN_SOUND_SPEED..MAX_SOUND_SPEED,
                            enabled = !sound.fixedPitch,
                            modifier = Modifier.weight(1f)
                        )
                        AudioValueText(
                            text = String.format(
                                Locale.ROOT,
                                "%.2fx",
                                if (sound.fixedPitch) sound.speed else localPitch
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    // 快捷倍速
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.5f, 0.8f, 1.0f, 1.2f, 1.5f).forEach { preset ->
                            AudioChip(
                                text = if (preset == 1.0f) {
                                    "1.0x"
                                } else {
                                    String.format(Locale.ROOT, "%.1fx", preset)
                                },
                                selected = kotlin.math.abs(sound.speed - preset) < 0.01f,
                                onClick = { playerViewModel.setSpeed(preset) }
                            )
                        }
                    }
                }
            }

            // ===== 高级设置 =====
            item {
                AudioSettingsCard(title = "高级设置", icon = Icons.Default.Tune) {
                    AudioSwitchRow(
                        title = "跳过静音",
                        subtitle = "自动跳过音频中的空白片段",
                        checked = sound.skipSilence,
                        onCheckedChange = { playerViewModel.setSkipSilence(it) }
                    )
                    AudioSwitchRow(
                        title = "响度均衡",
                        subtitle = "自动平衡不同歌曲的动态响度，避免音量骤变",
                        checked = normalization,
                        onCheckedChange = onNormalizationChange
                    )
                    AudioSwitchRow(
                        title = "32-bit 高解析输出",
                        subtitle = "优先使用高精度浮点处理音频（重启应用生效）",
                        checked = highRes,
                        onCheckedChange = onHighResChange
                    )
                }
            }
        }
    }
}

// ==================== 输出设备识别 ====================

private data class OutputDeviceInfo(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private fun detectOutputDevice(context: Context): OutputDeviceInfo {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val devices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()

    fun nameOf(type: Int, fallback: String): String? =
        devices.firstOrNull { it.type == type }
            ?.productName
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: devices.firstOrNull { it.type == type }?.let { fallback }

    nameOf(AudioDeviceInfo.TYPE_USB_DEVICE, "USB 音频设备")?.let {
        return OutputDeviceInfo(it, Icons.Default.SettingsInputComponent)
    }
    nameOf(AudioDeviceInfo.TYPE_USB_HEADSET, "USB 耳机")?.let {
        return OutputDeviceInfo(it, Icons.Default.Headset)
    }
    nameOf(AudioDeviceInfo.TYPE_USB_ACCESSORY, "USB 音频设备")?.let {
        return OutputDeviceInfo(it, Icons.Default.SettingsInputComponent)
    }
    nameOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "蓝牙音频设备")?.let {
        return OutputDeviceInfo(it, Icons.Default.SurroundSound)
    }
    nameOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "蓝牙通话设备")?.let {
        return OutputDeviceInfo(it, Icons.Default.SurroundSound)
    }
    nameOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "有线耳机")?.let {
        return OutputDeviceInfo(it, Icons.Default.Headphones)
    }
    nameOf(AudioDeviceInfo.TYPE_WIRED_HEADSET, "有线耳机")?.let {
        return OutputDeviceInfo(it, Icons.Default.Headset)
    }
    nameOf(AudioDeviceInfo.TYPE_HDMI, "HDMI 输出")?.let {
        return OutputDeviceInfo(it, Icons.Default.SettingsInputComponent)
    }
    return OutputDeviceInfo("手机扬声器", Icons.Default.VolumeUp)
}

private fun formatAudioDetail(format: Format?): String {
    if (format == null) return "等待播放"
    val codec = when {
        format.sampleMimeType?.contains("flac", true) == true -> "FLAC"
        format.sampleMimeType?.contains("mp4a", true) == true -> "AAC"
        format.sampleMimeType?.contains("mpeg", true) == true -> "MP3"
        format.sampleMimeType?.contains("opus", true) == true -> "Opus"
        format.sampleMimeType?.contains("vorbis", true) == true -> "Vorbis"
        format.sampleMimeType?.contains("pcm", true) == true -> "PCM"
        format.sampleMimeType != null -> format.sampleMimeType!!.substringAfterLast('/').uppercase()
        else -> "音频"
    }
    val parts = mutableListOf(codec)
    if (format.sampleRate > 0) {
        parts.add(String.format(Locale.ROOT, "%.1f kHz", format.sampleRate / 1000f))
    }
    if (format.channelCount > 0) {
        parts.add(
            when (format.channelCount) {
                1 -> "单声道"
                2 -> "立体声"
                else -> "${format.channelCount} 声道"
            }
        )
    }
    if (format.bitrate > 0) {
        parts.add("${format.bitrate / 1000} kbps")
    }
    return parts.joinToString(" · ")
}
