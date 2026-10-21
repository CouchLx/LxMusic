package com.example.lxmusic.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lxmusic.BuildConfig
import com.example.lxmusic.KuGouApi
import com.example.lxmusic.VipConfigManager
import com.example.lxmusic.data.SettingsRepository
import kotlinx.coroutines.launch

private const val PREFS = "settings"
private const val KEY_PROMPT_VERSION = "activation_prompt_version"

/** 跨页面 UI 事件：激活成功后引导用户去登录（MainActivity 观察并打开登录页） */
object AppUiEvents {
    var requestLogin by mutableStateOf(false)
}

/**
 * 启动门禁弹窗：有内置在线服务、但设备未激活时，让用户二选一：
 * - 输入激活码：解锁在线音乐（全量功能）
 * - 使用本地音乐：本次版本内不再打扰，在线功能保持不可用
 *
 * 每个版本提示一次；解绑后会重新提示。
 */
@Composable
fun ActivationGate(context: Context = LocalContext.current) {
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var visible by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var activating by remember { mutableStateOf(false) }
    var showLoginPrompt by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // 等启动初始化（VIP 恢复、在线服务地址）完成后再判断
        kotlinx.coroutines.delay(400)
        val status = VipConfigManager.getStatus(context)
        val promptedVersion = prefs.getString(KEY_PROMPT_VERSION, null)
        visible = KuGouApi.bffUrl.isNotBlank() &&
            !status.canUseVip &&
            promptedVersion != BuildConfig.VERSION_NAME
    }

    if (showLoginPrompt) {
        AlertDialog(
            onDismissRequest = { /* 必须选择 */ },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("激活成功") },
            text = {
                Text(
                    "在线音乐需要登录酷狗账号后才能使用。现在去登录吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLoginPrompt = false
                    visible = false
                    AppUiEvents.requestLogin = true
                }) { Text("去登录") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLoginPrompt = false
                    visible = false
                    SettingsRepository(context).requestHomeRefresh()
                }) { Text("稍后") }
            }
        )
        return
    }

    if (!visible) return

    AlertDialog(
        onDismissRequest = { /* 必须二选一 */ },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("选择使用方式") },
        text = {
            Column {
                Text(
                    text = "在线音乐服务已改为激活制：输入激活码可解锁全部在线功能；" +
                        "没有激活码也可以继续使用本地音乐（导入本地歌曲、播放、歌词等）。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = androidx.compose.ui.Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it; error = null },
                    label = { Text("激活码（可选）") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = if (error != null) {
                        { Text(error!!, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !activating && code.isNotBlank(),
                onClick = {
                    activating = true
                    error = null
                    scope.launch {
                        val result = VipConfigManager.activate(context, code.trim())
                        activating = false
                        if (result.isSuccess) {
                            prefs.edit().putString(KEY_PROMPT_VERSION, BuildConfig.VERSION_NAME).apply()
                            val authPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                            val loggedIn = !authPrefs.getString("token", "").isNullOrBlank() &&
                                authPrefs.getLong("userid", 0L) != 0L
                            if (loggedIn) {
                                SettingsRepository(context).requestHomeRefresh()
                                Toast.makeText(context, "激活成功，正在刷新首页", Toast.LENGTH_SHORT).show()
                                visible = false
                            } else {
                                // 未登录：引导去登录，否则首页拿不到账号相关数据，用户会误以为激活没生效
                                showLoginPrompt = true
                            }
                        } else {
                            error = result.exceptionOrNull()?.message ?: "激活失败，请检查激活码"
                        }
                    }
                }
            ) { Text(if (activating) "激活中…" else "激活并开始使用") }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    prefs.edit().putString(KEY_PROMPT_VERSION, BuildConfig.VERSION_NAME).apply()
                    Toast.makeText(context, "已进入本地音乐模式", Toast.LENGTH_SHORT).show()
                    visible = false
                }
            ) { Text("使用本地音乐") }
        }
    )
}
