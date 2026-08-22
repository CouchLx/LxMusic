package com.example.lxmusic.ui.pages

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.ui.components.IOLoadingIndicator
import com.example.lxmusic.KuGouApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginPage(onBack: () -> Unit, onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authPrefs = remember { context.getSharedPreferences("auth", Context.MODE_PRIVATE) }

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var codeSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }

    // 倒计时
    LaunchedEffect(codeSent, countdown) {
        if (codeSent && countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Logo
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Lx Music",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "登录后享受更多功能",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 手机号输入
        OutlinedTextField(
            value = phone,
            onValueChange = {
                if (it.length <= 11 && it.all { c -> c.isDigit() }) {
                    phone = it
                    errorMessage = null
                }
            },
            label = { Text("手机号") },
            placeholder = { Text("请输入手机号") },
            leadingIcon = {
                Icon(Icons.Default.Person, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 验证码输入
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = {
                    if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                        code = it
                        errorMessage = null
                    }
                },
                label = { Text("验证码") },
                placeholder = { Text("请输入验证码") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )

            Button(
                onClick = {
                    if (phone.length != 11) {
                        errorMessage = "请输入正确的手机号"
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            val response = KuGouApi.service.sendCaptcha(phone)
                            if (response.status == 1) {
                                codeSent = true
                                countdown = 60
                            } else {
                                errorMessage = response.data?.msg ?: "发送失败"
                            }
                        } catch (e: Exception) {
                            errorMessage = "网络错误: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && (countdown == 0 || !codeSent),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (countdown > 0) "${countdown}s" else if (codeSent) "重新发送" else "获取验证码"
                )
            }
        }

        // 错误信息
        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 登录按钮
        Button(
            onClick = {
                if (phone.length != 11) {
                    errorMessage = "请输入正确的手机号"
                    return@Button
                }
                if (code.isBlank()) {
                    errorMessage = "请输入验证码"
                    return@Button
                }
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val response = KuGouApi.service.loginWithPhone(phone, code)
                        Log.d("LxMusic", "登录响应: status=${response.status}, data=${response.data}")
                        if (response.status == 1 && response.data != null) {
                            val token = response.data.token
                            val userid = response.data.userid
                            Log.d("LxMusic", "登录成功: token=$token, userid=$userid")
                            // 保存基本登录信息
                            authPrefs.edit()
                                .putString("token", token)
                                .putString("nickname", response.data.nickname ?: "用户")
                                .putLong("userid", userid)
                                .putString("phone", phone)
                                .apply()
                            // 更新 API 认证信息
                            KuGouApi.token = token ?: ""
                            KuGouApi.userid = userid.toString()
                            // 切换账号后清掉上一个账号的「喜欢」歌单缓存
                            KuGouApi.clearKugouLikeCache()
                            // 清除旧用户的个性化推荐缓存
                            val homePrefs = context.getSharedPreferences("home_cache", Context.MODE_PRIVATE)
                            homePrefs.edit()
                                .remove("daily_songs_json")
                                .remove("vip_songs_json")
                                .remove("history_songs_json")
                                .remove("style_songs_json")
                                .apply()
                            // 获取用户详情（头像等）
                            try {
                                val detailResp = KuGouApi.service.getUserDetail(token ?: "", userid)
                                Log.d("LxMusic", "用户详情响应: status=${detailResp.status}, pic=${detailResp.data?.pic}, nickname=${detailResp.data?.nickname}")
                                if (detailResp.status == 1 && detailResp.data != null) {
                                    authPrefs.edit()
                                        .putString("pic", detailResp.data.pic)
                                        .putString("nickname", detailResp.data.nickname ?: response.data.nickname ?: "用户")
                                        .apply()
                                }
                            } catch (e: Exception) {
                                Log.e("LxMusic", "获取用户详情失败", e)
                            }
                            onLoginSuccess(authPrefs.getString("nickname", "用户") ?: "用户")
                        } else {
                            Log.e("LxMusic", "登录失败: status=${response.status}, errcode=${response.errcode}")
                            errorMessage = "登录失败，请检查验证码"
                        }
                    } catch (e: Exception) {
                        Log.e("LxMusic", "登录异常", e)
                        errorMessage = "网络错误: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading && phone.isNotBlank() && code.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                IOLoadingIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("登录", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 返回按钮
        TextButton(onClick = onBack) {
            Text("返回")
        }

        val navBarHeight = remember {
            val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }
        Spacer(modifier = Modifier.height(with(LocalDensity.current) { navBarHeight.toDp() }))
    }
}