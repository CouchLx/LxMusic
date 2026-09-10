package com.example.lxmusic.ui.components

/*
 * 睡眠定时弹窗内容（挂在播放器菜单 PlayerMenuSheet 的 "sleep" 子页）：
 * 未定时 = 设置视图（时长滑条 + 开始 + 自动延长开关 + 提示）；
 * 定时中 = 运行视图（大号倒计时 + 停止 + 提示）。
 * 倒计时状态来自 PlayerService.sleepTimerFlow（服务内轮询，退出页面不中断）。
 */

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.PlayerProgress
import com.example.lxmusic.SleepTimerUiState
import kotlin.math.roundToInt

/** 睡眠定时剩余时间格式化：≥1h → H:MM:SS（01:59:55），否则 M:SS（59:55）；延长态前缀 "+" */
internal fun formatSleepRemain(ms: Long, plus: Boolean = false): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    val prefix = if (plus) "+" else ""
    return if (h > 0L) {
        prefix + String.format("%02d:%02d:%02d", h, m, s)
    } else {
        prefix + String.format("%02d:%02d", m, s)
    }
}

@Composable
fun SleepTimerContent(
    sleepTimer: SleepTimerUiState,
    progress: PlayerProgress,
    onStart: (minutes: Int, extend: Boolean) -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playback", Context.MODE_PRIVATE) }
    // 记忆上次使用的时长与延长开关（开始时写入，下次打开恢复）
    var minutes by remember {
        mutableIntStateOf(prefs.getInt("sleep_timer_minutes", 30).coerceIn(5, 120))
    }
    var extend by remember { mutableStateOf(prefs.getBoolean("sleep_timer_extend", true)) }
    val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        if (!sleepTimer.active) {
            // ===== 设置视图 =====
            Text(
                text = "睡眠定时",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // 时长卡片：标签 + 数值 + 滑条（5-120 分钟，步进 5）
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "时长",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$minutes 分钟",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = {
                            minutes = ((it / 5f).roundToInt() * 5).coerceIn(5, 120)
                        },
                        valueRange = 5f..120f,
                        steps = 22,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    prefs.edit()
                        .putInt("sleep_timer_minutes", minutes)
                        .putBoolean("sleep_timer_extend", extend)
                        .apply()
                    onStart(minutes, extend)
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "开始",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 自动延长卡片
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "自动延长到整首歌播完",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "开启后，倒计时结束时若正在播放，会等当前歌曲播完再暂停；" +
                                "延长期间图标下方以 +分:秒 显示剩余时长；" +
                                "延长期间手动暂停、切歌会取消本次定时，并以提示告知",
                            style = MaterialTheme.typography.bodySmall,
                            color = hintColor
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = extend,
                        onCheckedChange = {
                            extend = it
                            prefs.edit().putBoolean("sleep_timer_extend", it).apply()
                        }
                    )
                }
            }
        } else {
            // ===== 运行视图 =====
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = cardColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 延长态显示当前歌剩余（duration - position，+前缀）；倒计时态显示定时剩余
                    val remainMs = if (sleepTimer.extending) {
                        (progress.durationMs - progress.positionMs).coerceAtLeast(0L)
                    } else {
                        sleepTimer.remainingMs
                    }
                    Text(
                        text = formatSleepRemain(remainMs, plus = sleepTimer.extending),
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onStop()
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "停止",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // 睡眠提示卡
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "当用于睡眠，建议降低设备音量且勿设置过长时间，以免影响听觉",
                style = MaterialTheme.typography.bodySmall,
                color = hintColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }
    }
}
