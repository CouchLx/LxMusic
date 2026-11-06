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
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
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
        ModernTopBarBackground(
            backdrop = backdrop,
            liquidGlass = liquidGlass,
            hasCustomWallpaper = hasCustomWallpaper,
            hazeState = hazeState,
            blurRadius = blurRadius,
            isDark = isDark,
            scrimBrush = scrimBrush,
            tintBrush = tintBrush
        )

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
 * 现代主题「我的」页悬浮顶栏：与首页共用同一套背景/胶囊材质。
 * 左侧固定标题「我的」，右侧只保留一个圆形设置按键（比胶囊内图标更大）；
 * 高度与首页一致（状态栏 + 54dp），保证三个顶层页面切换时顶栏不跳变。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernMineTopBar(
    onSettings: () -> Unit,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false,
    hasCustomWallpaper: Boolean = false,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val solidHeight = statusBarTop + 8.dp
    val fadeHeight = 46.dp
    val topBarHeight = solidHeight + fadeHeight
    val solidRatio = (solidHeight / topBarHeight).coerceIn(0.1f, 0.85f)
    val bgColor = MaterialTheme.colorScheme.background
    val primaryColor = MaterialTheme.colorScheme.primary

    val scrimBrush = remember(bgColor, isDark, hasCustomWallpaper, solidRatio) {
        if (hasCustomWallpaper) {
            val veil = if (isDark) Color.Black else Color.White
            verticalFadeBrush(
                color = veil,
                topAlpha = if (isDark) 0.34f else 0.42f,
                solidRatio = solidRatio
            )
        } else {
            verticalFadeBrush(color = bgColor, topAlpha = 1f, solidRatio = solidRatio)
        }
    }
    val tintBrush = remember(primaryColor, solidRatio) {
        verticalFadeBrush(color = primaryColor, topAlpha = 0.05f, solidRatio = solidRatio, steps = 6)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
    ) {
        ModernTopBarBackground(
            backdrop = backdrop,
            liquidGlass = liquidGlass,
            hasCustomWallpaper = hasCustomWallpaper,
            hazeState = hazeState,
            blurRadius = 20.dp,
            isDark = isDark,
            scrimBrush = scrimBrush,
            tintBrush = tintBrush
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop + 3.dp, bottom = 6.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧固定标题「我的」：直接压在顶栏模糊层上，不再包胶囊底
            Text(
                text = "我的",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )

            // 右侧：单个圆形设置按键（比原来的胶囊内图标更大）
            ModernCapsuleContainer(
                modifier = Modifier.size(42.dp),
                backdrop = backdrop,
                liquidGlass = liquidGlass,
                hasCustomWallpaper = hasCustomWallpaper
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSettings() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.title_settings),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * 顶栏背景材质：壁纸渐进磨砂 / 液态玻璃 / 纯色渐隐，首页与「我的」共用，
 * 保证两个页面切换时顶栏观感完全一致。
 */
@Composable
private fun ModernTopBarBackground(
    backdrop: Backdrop?,
    liquidGlass: Boolean,
    hasCustomWallpaper: Boolean,
    hazeState: HazeState?,
    blurRadius: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    scrimBrush: Brush,
    tintBrush: Brush
) {
    // 顶栏自身始终透明：无论有没有壁纸，都由内容层的 DstIn 渐隐统一处理「顶部渐变模糊」，
    // 保证两种状态下观感一致（有壁纸时露出壁纸、无壁纸时渐隐到背景色）。
    // 液态玻璃胶囊直接浮在这层之上，不再叠一层顶栏渐隐。
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

    // 浅色主题微染胶囊：强调用主题主色，次级图标/分隔线用表面前景色
    val capsuleAccent = MaterialTheme.colorScheme.primary
    val capsuleContent = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    val capsuleDivider = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

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
                ModernPlayEqIcon(playing = isThisPlaying, tint = capsuleAccent)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 模式文字
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.Bold,
                color = capsuleAccent,
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
                    tint = capsuleAccent
                )
            }

            // 细微半透明竖分割线
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(15.dp)
                    .background(capsuleDivider)
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
                    tint = capsuleContent,
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

    val capsuleContent = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    val capsuleAccent = MaterialTheme.colorScheme.primary
    val capsuleDivider = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

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
                    tint = if (isRefreshing) capsuleAccent else capsuleContent
                )
            }

            // 细微半透明竖分割线
            Box(
                modifier = Modifier
                    .width(0.8.dp)
                    .height(15.dp)
                    .background(capsuleDivider)
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
                    tint = capsuleContent
                )
            }
        }
    }
}

/**
 * 「歌词边缘渐隐」同款内容遮罩（DstIn）：
 * - 顶部：topBarPx 范围内完全不可见，随后按歌词节奏在 topTailPx 内渐显；
 * - 底部：bottomFadePx 范围内从可见渐隐到完全透明（露出背景/壁纸）。
 * 传入 0 表示该方向不做处理。
 */
internal fun Modifier.lyricEdgeFadeMask(
    topBarPx: Float,
    topTailPx: Float,
    bottomFadePx: Float
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val h = size.height.coerceAtLeast(1f)
        val stops = ArrayList<Pair<Float, Color>>()
        fun add(pos: Float, alpha: Float) {
            val p = (pos / h).coerceIn(0f, 1f)
            if (stops.isEmpty() || p > stops.last().first) {
                stops += p to Color.Black.copy(alpha = alpha)
            }
        }
        if (topBarPx > 0f && topTailPx > 0f) {
            add(0f, 0f)
            add(topBarPx * 0.60f, 0f)
            add(topBarPx * 0.75f, 0.08f)
            add(topBarPx * 0.88f, 0.22f)
            add(topBarPx, 0.45f)
            add(topBarPx + topTailPx * 0.35f, 0.68f)
            add(topBarPx + topTailPx * 0.65f, 0.86f)
            add(topBarPx + topTailPx, 1f)
        } else {
            add(0f, 1f)
        }
        if (bottomFadePx > 0f) {
            val start = (h - bottomFadePx).coerceAtLeast(0f)
            add(start, 1f)
            add(start + bottomFadePx * 0.35f, 0.70f)
            add(start + bottomFadePx * 0.65f, 0.38f)
            add(start + bottomFadePx * 0.85f, 0.12f)
            add(h, 0f)
        } else {
            add(h, 1f)
        }
        drawRect(brush = Brush.verticalGradient(*stops.toTypedArray()), blendMode = BlendMode.DstIn)
    }

/**
 * 歌词顶部渐隐同款遮罩（对应 SyncedLyricsView 的 verticalEdgeFade，这里取反用底色遮）：
 * 顶边完全不透明（内容一个字都看不见）→ 30% 处 88% → 65% 处 42% → 90% 处 6% → 底边完全透明。
 * 节奏与歌词上下边缘的渐隐一致，所以内容看起来是"从屏幕顶端隐入背景"。
 */
private fun lyricEdgeFadeBrush(color: Color): Brush = Brush.verticalGradient(
    0f to color.copy(alpha = 1f),
    0.60f to color.copy(alpha = 1f),
    0.75f to color.copy(alpha = 0.92f),
    0.88f to color.copy(alpha = 0.78f),
    1f to color.copy(alpha = TOP_BAR_FADE_BOTTOM_ALPHA)
)

/**
 * 顶栏渐隐在内容区的延伸尾巴高度：
 * 顶栏自身只到 0.55 透明度，剩下的在内容区顶部继续渐隐，让"渐渐隐"的过渡更长。
 */
internal val TopBarFadeTailHeight = 30.dp

/** 顶栏底边透明度（与内容区尾巴起点严格对齐，避免出现接缝） */
private const val TOP_BAR_FADE_BOTTOM_ALPHA = 0.55f

/** 内容区顶部的渐隐尾巴（背景色 → 透明），接在顶栏底边之后 */
internal fun topBarFadeTailBrush(color: Color): Brush = Brush.verticalGradient(
    0f to color.copy(alpha = TOP_BAR_FADE_BOTTOM_ALPHA),
    0.35f to color.copy(alpha = 0.32f),
    0.65f to color.copy(alpha = 0.14f),
    0.85f to color.copy(alpha = 0.04f),
    1f to color.copy(alpha = 0f)
)

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

    // 统一用与底部播放条胶囊相同的主题容器色（secondaryContainer），
    // 不再区分壁纸/纯色，也不做毛玻璃——背景图模式下与无背景保持同一套观感。
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val borderColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f)

    val shadowColor = if (isDark) {
        Color.Black.copy(alpha = 0.30f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    // 液态玻璃：与悬浮底栏/迷你播放条同一套 drawBackdrop（vibrancy + 4dp 模糊 + lens 折射）。
    // 只要求「液态玻璃开 + 有 backdrop」——不要求必须有自定义壁纸：
    // 纯色主题下也要有玻璃质感，否则开关看起来像没生效。
    val glassBackdrop = backdrop
    val glassEnabled = liquidGlass && glassBackdrop != null
    // 玻璃表面色在组合期取出（draw lambda 里不能读 MaterialTheme）
    val glassSurface = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.18f)

    if (glassEnabled) {
        Box(
            modifier = modifier.drawBackdrop(
                backdrop = glassBackdrop!!,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(22.dp.toPx(), 22.dp.toPx())
                },
                highlight = {
                    Highlight.Default.copy(alpha = 1f)
                },
                shadow = {
                    Shadow.Default.copy(
                        color = Color.Black.copy(if (isDark) 0.14f else 0.06f)
                    )
                },
                onDrawSurface = {
                    drawRect(glassSurface)
                }
            ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .shadow(
                    elevation = 2.5.dp,
                    shape = CircleShape,
                    ambientColor = shadowColor,
                    spotColor = shadowColor
                )
                .background(containerColor, CircleShape)
                .border(width = 1.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
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
