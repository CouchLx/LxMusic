package com.example.lxmusic.ui.components.topbar

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.lxmusic.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * 现代主题专属首页悬浮顶栏（灵动双胶囊 + 100% 透明通透底栏）：
 * - 左侧：一键播放模式精美悬浮胶囊（律动条 + 模式名 + 播放/暂停 + 模式抽屉切换）
 * - 右侧：双按键对称悬浮胶囊（刷新 + 搜索）
 * - 中间与背景：100% 完全透明无遮挡，允许下方滚动内容自由穿透并呈现毛玻璃磨砂视效
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernHomeTopBar(
    homePlayMode: Int,
    onModeChange: (Int) -> Unit,
    isThisPlaying: Boolean,
    hasSongs: Boolean,
    onPlayAll: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onSearch: () -> Unit,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false,
    hasCustomWallpaper: Boolean = false,
    hazeState: HazeState? = null,
    scrollProgress: Float = 0f,
    modifier: Modifier = Modifier
) {
    var showModeSheet by remember { mutableStateOf(false) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 明暗判定跟随应用主题（而不是系统设置）：应用内强制深色/浅色时也要一致
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 几何分段：状态栏及其下方 8dp 区域为遮蔽基座，之后向下 46dp 为渐进消融过渡区
    val solidHeight = statusBarTop + 8.dp
    val fadeHeight = 46.dp
    val topBarHeight = solidHeight + fadeHeight
    val solidRatio = (solidHeight / topBarHeight).coerceIn(0.1f, 0.85f)
    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary
    // 首页列表滚动进度（0 = 停在顶部，1 = 已有内容滑到顶栏下方）
    val scrollFraction = scrollProgress.coerceIn(0f, 1f)

    // 磨砂强度随滚动轻微增强：静止时尽量保持壁纸通透，滚动时保证状态栏信息清晰
    val blurRadius by animateDpAsState(
        targetValue = (20f + 8f * scrollFraction).dp,
        animationSpec = tween(240),
        label = "home_top_bar_blur"
    )

    // 顶栏底色 / 壁纸模式下的对比纱罩
    val scrimBrush = remember(bgColor, solidRatio, isDark, hasCustomWallpaper, scrollFraction) {
        if (hasCustomWallpaper) {
            // 壁纸模式：模糊交给 haze，这里只留一层极轻的纱罩。
            // 浅色主题用白纱（状态栏是深色图标）、深色主题用黑纱，
            // 只压住状态栏那一小段并平滑淡出，不整块压暗壁纸。
            val veil = if (isDark) Color.Black else Color.White
            val topAlpha = (if (isDark) 0.34f else 0.42f) * (1f + 0.16f * scrollFraction)
            verticalFadeBrush(color = veil, topAlpha = topAlpha, solidRatio = solidRatio)
        } else {
            verticalFadeBrush(color = bgColor, topAlpha = 1f, solidRatio = solidRatio)
        }
    }

    // 主题色微染：把顶栏拉回品牌色域，透明收尾，肉眼几乎不可见但整体更统一
    val tintBrush = remember(primaryColor, solidRatio) {
        verticalFadeBrush(color = primaryColor, topAlpha = 0.05f, solidRatio = solidRatio, steps = 6)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
    ) {
        val isGlass = liquidGlass && backdrop != null && hasCustomWallpaper
        val useWallpaperHaze = hasCustomWallpaper && hazeState != null
        when {
            // 壁纸模式：渐进模糊（顶部满强度 → 底边强度归零）+ 极轻纱罩。
            // 模糊由 haze 从背景层实时采样，滑到顶栏下方的内容会一起被磨砂掉，
            // 而不是被一块半透明色直接盖住。
            useWallpaperHaze -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                backgroundColor = Color.Unspecified,
                                tints = emptyList(),
                                blurRadius = blurRadius,
                                noiseFactor = if (isDark) 0.07f else 0.05f
                            )
                        ) {
                            // 顶边就是屏幕边缘，钳制采样，避免把屏外透明像素糊进来
                            blurredEdgeTreatment = BlurredEdgeTreatment.Rectangle
                            progressive = HazeProgressive.verticalGradient(
                                easing = LinearEasing,
                                startY = 0f,
                                startIntensity = 1f,
                                endY = Float.POSITIVE_INFINITY,
                                endIntensity = 0f
                            )
                        }
                )
                Box(modifier = Modifier.fillMaxSize().background(scrimBrush))
                Box(modifier = Modifier.fillMaxSize().background(tintBrush))
            }
            isGlass -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RectangleShape },
                            effects = {
                                vibrancy()
                                blur(10.dp.toPx())
                            },
                            onDrawSurface = {
                                drawRect(brush = scrimBrush)
                                drawRect(brush = tintBrush)
                            }
                        )
                )
            }
            else -> {
                // 纯色主题：只留背景色 → 透明的垂直渐变（内容从顶栏下柔和淡出），
                // 不叠模糊/主题微染，避免在纯色背景上出现一块灰色"浆糊"
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimBrush)
                )
            }
        }

        // 浮在羽化层上方的悬浮双胶囊 Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop + 3.dp, bottom = 6.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：一体化一键播放模式悬浮胶囊
            ModernPlayModeCapsule(
                homePlayMode = homePlayMode,
                isThisPlaying = isThisPlaying,
                hasSongs = hasSongs,
                onPlayAll = onPlayAll,
                onShowModeSheet = { showModeSheet = true },
                backdrop = backdrop,
                liquidGlass = liquidGlass,
                hasCustomWallpaper = hasCustomWallpaper
            )

            // 右侧：刷新 + 搜索 双功能一体化悬浮胶囊
            ModernActionCapsule(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onSearch = onSearch,
                backdrop = backdrop,
                liquidGlass = liquidGlass,
                hasCustomWallpaper = hasCustomWallpaper
            )
        }
    }

    // 播放模式切换底部抽屉
    if (showModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Text(
                text = "一键播放模式",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            val modes = listOf(
                Triple(Icons.AutoMirrored.Filled.QueueMusic, "顺序", "按当前列表顺序播放"),
                Triple(Icons.Default.Shuffle, "随机", "打乱列表顺序播放"),
                Triple(Icons.Default.Favorite, "心动", "随机播放，遇见心动的歌")
            )
            modes.forEachIndexed { mode, (icon, label, desc) ->
                val isSelected = homePlayMode == mode
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable {
                            onModeChange(mode)
                            showModeSheet = false
                        }
                        .padding(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                icon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * 左侧一键播放模式胶囊
 */
@Composable
private fun ModernPlayModeCapsule(
    homePlayMode: Int,
    isThisPlaying: Boolean,
    hasSongs: Boolean,
    onPlayAll: () -> Unit,
    onShowModeSheet: () -> Unit,
    backdrop: Backdrop?,
    liquidGlass: Boolean,
    hasCustomWallpaper: Boolean = false,
    modifier: Modifier = Modifier
) {
    val modeLabel = when (homePlayMode) {
        1 -> "随机"
        2 -> "心动"
        else -> "顺序"
    }

    ModernCapsuleContainer(
        modifier = modifier
            .alpha(if (hasSongs) 1f else 0.45f)
            .height(42.dp),
        backdrop = backdrop,
        liquidGlass = liquidGlass,
        hasCustomWallpaper = hasCustomWallpaper
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp)
        ) {
            // 律动或音符槽位
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(20.dp)
            ) {
                ModernPlayEqIcon(playing = isThisPlaying)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 模式文字
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPlayAll() }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )

            // 播放/暂停快捷按钮
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onPlayAll() }
            ) {
                Icon(
                    imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isThisPlaying) "暂停" else stringResource(R.string.action_play_all),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 细微半透明竖分割线
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(15.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            )

            // 下拉展开切换模式
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onShowModeSheet() }
            ) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "切换播放模式",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 右侧对称双按键胶囊（刷新 + 搜索）
 */
@Composable
private fun ModernActionCapsule(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSearch: () -> Unit,
    backdrop: Backdrop?,
    liquidGlass: Boolean,
    hasCustomWallpaper: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 刷新时的优雅旋转动画
    val infiniteTransition = rememberInfiniteTransition(label = "refresh_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation"
    )

    ModernCapsuleContainer(
        modifier = modifier.height(42.dp),
        backdrop = backdrop,
        liquidGlass = liquidGlass,
        hasCustomWallpaper = hasCustomWallpaper
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            // 刷新按钮
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = !isRefreshing,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onRefresh() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    modifier = Modifier
                        .size(19.dp)
                        .graphicsLayer {
                            if (isRefreshing) rotationZ = spinAngle
                        },
                    tint = if (isRefreshing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }

            // 细微半透明竖分割线
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(15.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            )

            // 搜索按钮
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSearch() }
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.title_search),
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/**
 * 垂直渐隐画刷：从顶部到 [solidRatio] 保持恒定透明度，之后用 smoothstep 平滑淡到 0。
 *
 * 普通的分段线性渐变（恒定 → 中段 → 透明）在色标处斜率会突变，
 * 人眼在平滑背景（壁纸、磨砂层）上会把这条折点看成一条横向"接缝"（Mach band）。
 * smoothstep 在首尾导数都为 0，正好与上方恒定段、下方透明段无缝衔接。
 */
private fun verticalFadeBrush(
    color: Color,
    topAlpha: Float,
    solidRatio: Float,
    steps: Int = 12
): Brush {
    val stops = ArrayList<Pair<Float, Color>>(steps + 2)
    stops += 0f to color.copy(alpha = topAlpha)
    stops += solidRatio to color.copy(alpha = topAlpha)
    for (i in 1..steps) {
        val u = i / steps.toFloat()
        val fall = 1f - u * u * (3f - 2f * u)
        stops += (solidRatio + (1f - solidRatio) * u) to color.copy(alpha = topAlpha * fall)
    }
    return Brush.verticalGradient(*stops.toTypedArray())
}

/**
 * 统一毛玻璃磨砂悬浮胶囊基座容器
 */
@Composable
private fun ModernCapsuleContainer(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false,
    hasCustomWallpaper: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // 胶囊背景材质：壁纸模式半透明玻璃；纯色主题用不透明表面色 + 柔和阴影（参考图样式）
    val containerColor = if (hasCustomWallpaper) {
        if (isDark) {
            Color.Black.copy(alpha = 0.42f)
        } else {
            Color.White.copy(alpha = 0.65f)
        }
    } else {
        if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surface
        }
    }

    // 胶囊外轮廓高光边框：壁纸模式下更明显；纯色主题浅色不描边，深色留一点提亮
    val borderColor = if (hasCustomWallpaper) {
        if (isDark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.85f)
    } else {
        if (isDark) Color.White.copy(alpha = 0.10f) else Color.Transparent
    }

    val shadowColor = if (isDark) {
        Color.Black.copy(alpha = 0.30f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    val isGlassEnabled = liquidGlass && backdrop != null && hasCustomWallpaper

    val glassModifier = if (isGlassEnabled) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { CircleShape },
            effects = {
                vibrancy()
                blur(8.dp.toPx())
                lens(18.dp.toPx(), 18.dp.toPx())
            },
            highlight = {
                Highlight.Default.copy(alpha = 0.9f)
            },
            shadow = {
                Shadow.Default.copy(color = shadowColor)
            },
            onDrawSurface = {
                drawRect(containerColor)
            }
        )
    } else {
        Modifier
            .shadow(
                elevation = 2.5.dp,
                shape = CircleShape,
                ambientColor = shadowColor,
                spotColor = shadowColor
            )
            .background(containerColor, CircleShape)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .then(glassModifier)
            .border(width = 1.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 律动 EQ 指示器
 */
@Composable
private fun ModernPlayEqIcon(
    playing: Boolean,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    if (playing) {
        val transition = rememberInfiniteTransition(label = "modern_home_eq")
        val b1 by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq1"
        )
        val b2 by transition.animateFloat(
            initialValue = 0.9f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq2"
        )
        val b3 by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
            label = "eq3"
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.size(width = 14.dp, height = 14.dp)
        ) {
            listOf(b1, b2, b3).forEach { fraction ->
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .fillMaxHeight(fraction.coerceIn(0.2f, 1f))
                        .clip(RoundedCornerShape(1.dp))
                        .background(tint)
                )
            }
        }
    } else {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = tint.copy(alpha = 0.9f)
        )
    }
}
