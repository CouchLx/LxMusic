package com.example.lxmusic.ui.pages

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.NeteaseApi
import com.example.lxmusic.NeteaseAuth
import com.example.lxmusic.NeteaseProfile
import com.example.lxmusic.ui.components.IOLoadingIndicator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 网易云品牌红（仅用于登录页标识，跟随主题不参与配色体系） */
private val NeteaseRed = Color(0xFFE60026)

/**
 * 网易云登录页（全屏覆盖层）。
 *
 * 扫码为主、手机验证码兜底：NCM 的扫码链路最稳（验证码受风控影响更大）。
 * 登录成功后把 cookie 交给 [NeteaseAuth] 落盘，后续请求由 NeteaseApi 拦截器注入。
 */
@Composable
fun NeteaseLoginPage(onBack: () -> Unit, onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // 扫码登录
    var qrBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var qrStatus by remember { mutableStateOf("正在获取二维码…") }
    var qrExpired by remember { mutableStateOf(false) }
    var qrRefreshTick by remember { mutableIntStateOf(0) }

    // 手机验证码登录
    var showPhoneForm by remember { mutableStateOf(false) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }

    /**
     * 落盘登录态。
     *
     * 扫码 803 的 body cookie 是多份 Set-Cookie 拼起来的（带 Max-Age/Path 等属性），
     * 必须先 cleanCookie 再发请求；同时拿拦截器从响应头合并出来的那份做兜底
     * （两份都只有 name=value，谁能让 /user/account 返回 profile 就用谁）。
     */
    suspend fun finishLogin(bodyCookie: String, profileHint: NeteaseProfile?): Boolean {
        val before = NeteaseApi.authCookie
        val candidates = listOf(bodyCookie, before)
            .filter { it.isNotBlank() }
            .map { NeteaseApi.cleanCookie(it) }
            .filter { it.contains("MUSIC_U=") }
            .distinct()
        if (candidates.isEmpty()) {
            errorMessage = "登录失败：服务器未返回登录凭证"
            return false
        }

        var chosenCookie = candidates.first()
        var uid = profileHint?.userId ?: 0L
        var nickname = profileHint?.nickname.orEmpty()
        var avatar = profileHint?.avatarUrl.orEmpty()
        var vipType = profileHint?.vipType ?: 0

        for (cookie in candidates) {
            NeteaseApi.authCookie = cookie
            val acc = runCatching {
                NeteaseApi.service.userAccount(System.currentTimeMillis())
            }.getOrNull()?.takeIf { it.profile != null } ?: continue
            val profile = acc.profile ?: continue
            chosenCookie = cookie
            if (profile.userId != 0L) uid = profile.userId
            if (!profile.nickname.isNullOrBlank()) nickname = profile.nickname
            if (!profile.avatarUrl.isNullOrBlank()) avatar = profile.avatarUrl
            vipType = profile.vipType
            if (uid == 0L) acc.account?.id?.let { uid = it }
            break
        }

        // 验证码登录的响应体自带 profile，账号接口抖动时仍可完成登录
        if (uid == 0L) {
            NeteaseApi.authCookie = before
            errorMessage = "登录失败：未取到账号信息，请重试"
            return false
        }

        val finalName = nickname.ifBlank { "网易云用户" }
        NeteaseAuth.persist(context, chosenCookie, uid, finalName, avatar, vipType)
        Log.d("LxMusic", "网易云登录成功: uid=$uid, nickname=$finalName")
        onLoginSuccess(finalName)
        return true
    }

    // 二维码：取 key → 生成图片 → 每 2 秒轮询状态（所有请求带 timestamp，绕开 NCM 2 分钟缓存）
    LaunchedEffect(qrRefreshTick) {
        qrExpired = false
        qrBitmap = null
        qrStatus = "正在获取二维码…"
        errorMessage = null

        val keyResp = runCatching { NeteaseApi.service.qrKey(System.currentTimeMillis()) }.getOrNull()
        val key = keyResp?.data?.loginKey
        if (key.isNullOrBlank()) {
            qrStatus = "获取二维码失败，请点「刷新二维码」重试"
            qrExpired = true
            return@LaunchedEffect
        }

        val createResp = runCatching {
            NeteaseApi.service.qrCreate(key, true, System.currentTimeMillis())
        }.getOrNull()
        qrBitmap = decodeNeteaseQrDataUri(createResp?.data?.qrimg)
        if (qrBitmap == null) {
            qrStatus = "二维码生成失败，请刷新重试"
            qrExpired = true
            return@LaunchedEffect
        }
        qrStatus = "打开网易云音乐 App，扫码并确认登录"

        while (true) {
            delay(2000)
            var resp = runCatching {
                NeteaseApi.service.qrCheck(key, System.currentTimeMillis())
            }.getOrNull()
            // 文档：扫码后如返回 502，需加 noCookie=true 重试（此时 cookie 只能从 Set-Cookie 抓）
            if (resp != null && resp.code == 502) {
                resp = runCatching {
                    NeteaseApi.service.qrCheck(key, System.currentTimeMillis(), true)
                }.getOrNull()
            }
            if (resp == null) continue
            when (resp.code) {
                800 -> {
                    qrStatus = "二维码已过期，请刷新"
                    qrExpired = true
                    break
                }
                801 -> qrStatus = "等待扫码…"
                802 -> qrStatus = "已扫码，请在手机上确认登录"
                803 -> {
                    qrStatus = "登录成功，正在同步账号…"
                    if (!finishLogin(resp.cookie.orEmpty(), null)) qrExpired = true
                    break
                }
                else -> qrStatus = resp.message?.takeIf { it.isNotBlank() } ?: "等待扫码…"
            }
        }
    }

    // 验证码倒计时
    LaunchedEffect(codeSent, countdown) {
        if (codeSent && countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Surface(
            modifier = Modifier.size(96.dp),
            shape = RoundedCornerShape(24.dp),
            color = NeteaseRed.copy(alpha = 0.14f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = NeteaseRed
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "网易云音乐",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "登录后同步歌单与每日推荐",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        // 二维码（固定白底，暗色主题下也能扫）
        Surface(
            modifier = Modifier.size(220.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White
        ) {
            Box(contentAlignment = Alignment.Center) {
                val bitmap = qrBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "网易云登录二维码",
                        modifier = Modifier.fillMaxSize().padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    IOLoadingIndicator()
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = qrStatus,
            style = MaterialTheme.typography.bodyMedium,
            color = if (qrExpired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )

        if (qrExpired) {
            TextButton(onClick = { qrRefreshTick++ }) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新二维码")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "或",
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        TextButton(onClick = { showPhoneForm = !showPhoneForm }) {
            Text(if (showPhoneForm) "收起验证码登录" else "使用手机验证码登录")
        }

        if (showPhoneForm) {
            Spacer(modifier = Modifier.height(8.dp))

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
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
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
                                val resp = NeteaseApi.service.sendCaptcha(phone, System.currentTimeMillis())
                                if (resp.code == 200) {
                                    codeSent = true
                                    countdown = 60
                                } else {
                                    errorMessage = resp.reason ?: neteaseErrorText(resp.code)
                                }
                            } catch (e: Exception) {
                                Log.e("LxMusic", "网易云发送验证码失败", e)
                                errorMessage = friendlyError(e)
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

            Spacer(modifier = Modifier.height(20.dp))

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
                            val resp = NeteaseApi.service.loginCellphone(
                                phone = phone,
                                captcha = code,
                                timestamp = System.currentTimeMillis()
                            )
                            if (resp.code == 200) {
                                if (!finishLogin(resp.cookie.orEmpty(), resp.profile)) {
                                    isLoading = false
                                }
                            } else {
                                errorMessage = resp.reason ?: neteaseErrorText(resp.code)
                            }
                        } catch (e: Exception) {
                            Log.e("LxMusic", "网易云验证码登录失败", e)
                            errorMessage = friendlyError(e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    IOLoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("登录", fontSize = 16.sp)
                }
            }
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onBack) { Text("返回") }

        val navBarHeight = remember {
            val resId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        }
        Spacer(modifier = Modifier.height(with(LocalDensity.current) { navBarHeight.toDp() } + 24.dp))
    }
}

/** 解码网易云二维码（data:image/png;base64,...） */
private fun decodeNeteaseQrDataUri(uri: String?): ImageBitmap? {
    return runCatching {
        val base64 = uri?.substringAfter("base64,", "") ?: return null
        if (base64.isBlank()) return null
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

/** NCM 业务错误码 → 中文 */
private fun neteaseErrorText(code: Int): String = when (code) {
    400 -> "请求参数错误"
    301, -462 -> "登录已失效，请重新登录"
    501 -> "该手机号未注册网易云账号"
    502 -> "验证码或密码错误"
    503 -> "验证码错误或已过期"
    509 -> "请求过于频繁，请稍后再试"
    -460 -> "触发风控校验，请稍后再试"
    else -> "登录失败（错误码 $code）"
}

/** 网络/HTTP 异常 → 可展示文案（BFF 的 401/503 是运维侧最常见的两个） */
private fun friendlyError(e: Throwable): String = when (e) {
    is retrofit2.HttpException -> when (e.code()) {
        401 -> "未激活或激活已过期，请先在设置中激活"
        502 -> "网易云服务暂时不可用，请稍后再试"
        503 -> "服务器未配置网易云适配器（NETEASE_BASE_URL）"
        else -> "服务器返回 ${e.code()}"
    }
    else -> e.message?.takeIf { it.isNotBlank() } ?: "网络错误"
}
