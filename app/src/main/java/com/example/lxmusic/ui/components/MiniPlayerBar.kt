package com.example.lxmusic.ui.components

import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.PlayerProgress
import com.example.lxmusic.R
import com.example.lxmusic.ui.theme.LocalScaleFactor
import com.example.lxmusic.ui.theme.ScreenAdapter
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 重写的播放条组件 - 简化实现，彻底解决低透明度下的视觉问题
 * 新增：进度条、上滑展开播放器、喜欢按钮
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MiniPlayerBar(
    song: SongInfo,
    isPlaying: Boolean,
    progress: StateFlow<PlayerProgress> = MutableStateFlow(PlayerProgress()),
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit = {},
    onClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    // 纵向跟手拖动（上滑展开播放器）：开始/每帧位移(上滑为负)/结束/取消
    onVerticalDragStart: () -> Unit = {},
    onVerticalDrag: (Float) -> Unit = {},
    onVerticalDragEnd: () -> Unit = {},
    onVerticalDragCancel: () -> Unit = {},
    bottomPadding: Dp = 120.dp,
    isFloatingBottomBar: Boolean = false,
    followThemeColor: Boolean = false,
    playerBarOpacity: Float = 1f,
    playerBarWhiteBlend: Float = 0.8f,
    liquidGlass: Boolean = false,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val progressState by progress.collectAsState()
    val currentPosition = progressState.positionMs
    val totalDuration = progressState.durationMs
    var swipeDirection by remember { mutableIntStateOf(0) }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    // 横滑跟手位移（歌名信息跟手平移；0 = 归位）
    val dragOffset = remember { Animatable(0f) }
    // 切换触发距离（跟手位移超过该值，松手即切换）
    val switchThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    // 横滑速度追踪：轻快滑动（位移小但速度快）也能触发切换
    val swipeVelocityTracker = remember { VelocityTracker() }
    // 拖动方向（-1=下一首左滑，1=上一首右滑，0=无）；derivedStateOf 只在方向变化时触发重组
    val dragDirection by remember {
        derivedStateOf {
            when {
                dragOffset.value < -1f -> -1
                dragOffset.value > 1f -> 1
                else -> 0
            }
        }
    }

    // 获取屏幕缩放比例
    val contentScaleFactor = LocalScaleFactor.current  // 用于图标、图片等内容
    val spacingScaleFactor = ScreenAdapter.getSpacingScaleFactor()  // 用于间距、边距

    // 尺寸配置 - 内容尺寸随屏幕放大，间距保持固定
    // 原生主题（非悬浮）：对齐 Neri 风格（顶部圆角 20dp、封面 40dp、无阴影）
    // 现代化主题（悬浮）：保持原有胶囊样式
    val cornerRadius = if (isFloatingBottomBar) 32.dp * contentScaleFactor else 20.dp * contentScaleFactor
    // 悬浮模式阴影：减小高度 + 半透明柔和色，避免突兀的纯黑阴影
    val shadowElevation = if (isFloatingBottomBar) 3.dp * contentScaleFactor else 0.dp
    val albumSize = if (isFloatingBottomBar) 50.dp * contentScaleFactor else 40.dp * contentScaleFactor
    val albumCornerRadius = if (isFloatingBottomBar) 10.dp else 8.dp
    // 形状：悬浮=全圆角胶囊；原生=仅顶部圆角（贴底卡片）
    val barShape = if (isFloatingBottomBar) {
        RoundedCornerShape(cornerRadius)
    } else {
        RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    }
    // 内容颜色：原生主题用 onSecondaryContainer（对齐 Neri）
    val titleColor = if (isFloatingBottomBar) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val artistColor = if (isFloatingBottomBar) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
    }

    // 间距保持固定（不随屏幕变化）
    val horizontalPadding = 14.dp
    val verticalPadding = if (isFloatingBottomBar) 4.dp else 8.dp

    // 背景色计算：直接使用 MaterialTheme.colorScheme.secondaryContainer，随主题/专辑封面实时变色
    val barOpacity = playerBarOpacity.coerceIn(0f, 1f)
    val backgroundColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = barOpacity)

    // 进度计算
    val progress = if (totalDuration > 0) {
        (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 主题色
    val primaryColor = MaterialTheme.colorScheme.primary

    // 液态玻璃：开启且背景层可用时启用（与导航栏保持一致的苹果风格）
    val isDarkTheme = isSystemInDarkTheme()
    val glassActive = liquidGlass && backdrop != null
    // 玻璃容器色：半透明材质并注入当前主题/专辑微光 tint
    val glassContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(
        alpha = (0.24f * barOpacity).coerceIn(0.08f, 1f)
    )

    // 适配后的底部间距：直接使用调用方传入的实测值（已在 MainActivity 统一处理缩放）
    val adaptedBottomPadding = bottomPadding

    Box(
        modifier = modifier
            .then(
                if (isFloatingBottomBar) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .navigationBarsPadding()
            .padding(
                start = if (isFloatingBottomBar) 16.dp else 6.dp,
                end = if (isFloatingBottomBar) 16.dp else 6.dp,
                bottom = if (adaptedBottomPadding == 0.dp && isFloatingBottomBar) 8.dp else adaptedBottomPadding
            )
    ) {
        // 主容器 - 使用 Box 包裹，进度条绝对贴在最底部，没有任何底部空隙/白边
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFloatingBottomBar) 64.dp else 58.dp)
                .then(
                    if (glassActive) {
                        // NPatch 同款液态玻璃背景：鲜明度 + 4dp 柔和模糊 + 24dp 凸透镜晶莹折射 + 边缘高光
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { barShape },
                            effects = {
                                vibrancy()
                                blur(4.dp.toPx())
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    alpha = if (isDarkTheme) 0.12f else 0.22f
                                )
                            },
                            shadow = {
                                Shadow.Default.copy(
                                    color = Color.Black.copy(if (isDarkTheme) 0.12f else 0.06f)
                                )
                            },
                            onDrawSurface = {
                                drawRect(glassContainerColor)
                                drawRect(primaryColor.copy(alpha = if (isDarkTheme) 0.10f else 0.08f))
                            }
                        )
                    } else {
                        Modifier
                            .shadow(
                                elevation = shadowElevation,
                                shape = barShape,
                                clip = false,
                                // 柔和阴影色（半透明黑），避免突兀的纯黑阴影
                                ambientColor = Color.Black.copy(alpha = 0.18f),
                                spotColor = Color.Black.copy(alpha = 0.22f)
                            )
                            .clip(barShape)
                            .drawBehind {
                                drawRect(backgroundColor)
                            }
                    }
                )
                // 统一手势 + 方向锁定：横滑（切歌）与上拉（展开）互不误触。
                // 累计位移超过 touchSlop 时按 |dx| vs |dy| 锁定方向，锁定后只分发该轴，
                // 斜向滑动的另一方向分量被丢弃
                .pointerInput(Unit) {
                    var lockedAxis = 0  // 0=未锁定, 1=水平, 2=垂直
                    var pendingX = 0f
                    var pendingY = 0f
                    detectDragGestures(
                        onDragStart = {
                            lockedAxis = 0
                            pendingX = 0f
                            pendingY = 0f
                        },
                        onDragEnd = {
                            when (lockedAxis) {
                                1 -> {
                                    // 水平结束：切歌判定（位移阈值或轻快速度）
                                    val current = dragOffset.value
                                    val velocityX = swipeVelocityTracker.calculateVelocity().x
                                    val shouldSwitch =
                                        abs(current) > switchThresholdPx * 0.65f || abs(velocityX) > 600f
                                    if (shouldSwitch) {
                                        context.performHapticFeedback(HapticFeedbackEffect.Click)
                                        if (current < 0) {
                                            swipeDirection = -1
                                            onNext()
                                        } else {
                                            swipeDirection = 1
                                            onPrevious()
                                        }
                                    }
                                    // 切换后归位 / 未超阈值回弹：弹簧复位
                                    scope.launch {
                                        dragOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                                2 -> onVerticalDragEnd()
                            }
                        },
                        onDragCancel = {
                            when (lockedAxis) {
                                1 -> scope.launch {
                                    dragOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                                2 -> onVerticalDragCancel()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            val dx = dragAmount.x
                            val dy = dragAmount.y
                            if (lockedAxis == 0) {
                                // 方向锁定：按累计位移绝对值比较
                                pendingX += dx
                                pendingY += dy
                                val slop = viewConfiguration.touchSlop
                                if (abs(pendingX) > slop || abs(pendingY) > slop) {
                                    lockedAxis = if (abs(pendingX) > abs(pendingY)) 1 else 2
                                    if (lockedAxis == 1) {
                                        // 水平锁定：初始化横滑状态并补发积累位移
                                        dragAccumulated = 0f
                                        swipeVelocityTracker.resetTracking()
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            dragOffset.stop()
                                        }
                                        dragAccumulated += pendingX
                                        swipeVelocityTracker.addPosition(
                                            SystemClock.uptimeMillis(),
                                            Offset(dragAccumulated, 0f)
                                        )
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            dragOffset.snapTo(dragAccumulated)
                                        }
                                    } else {
                                        // 垂直锁定：上拉展开
                                        onVerticalDragStart()
                                        onVerticalDrag(pendingY)
                                    }
                                    pendingX = 0f
                                    pendingY = 0f
                                }
                            } else if (lockedAxis == 1) {
                                change.consume()
                                dragAccumulated += dx
                                swipeVelocityTracker.addPosition(
                                    SystemClock.uptimeMillis(),
                                    Offset(dragAccumulated, 0f)
                                )
                                // 跟手：立即更新歌名位移（UNDISPATCHED，无动画延迟）
                                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    dragOffset.snapTo(dragAccumulated)
                                }
                            } else {
                                change.consume()
                                onVerticalDrag(dy)
                            }
                        }
                    )
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { onClick() }
        ) {
            // 内容区域
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isFloatingBottomBar) 10.dp else 12.dp,
                        end = if (isFloatingBottomBar) 10.dp else 12.dp,
                        top = 4.dp,
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 专辑封面：切歌时自然淡入淡出刷新（不滑动，渐变过渡）
                AnimatedContent(
                    targetState = song,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    contentKey = { it.filePath },
                    label = "albumAnim"
                ) { targetSong ->
                    AlbumCoverSimple(
                        filePath = targetSong.filePath,
                        albumArtUri = targetSong.albumArtUri,
                        size = if (isFloatingBottomBar) 48.dp else 40.dp,
                        cornerRadius = if (isFloatingBottomBar) 12.dp else 8.dp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 中间"卡片窗口"：歌名/歌手居中对齐，左右滑动时歌名被推挤出视野，下一首/上一首文本跟手推入
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val currentDrag = dragOffset.value

                    // 右滑时：从左侧推入"上一首"
                    Text(
                        text = "上一首",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = primaryColor,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                translationX = -switchThresholdPx + currentDrag
                                alpha = if (currentDrag > 0f) {
                                    (currentDrag / (switchThresholdPx * 0.6f)).coerceIn(0f, 1f)
                                } else 0f
                            }
                    )

                    // 歌名/歌手信息：垂直居中，跟手平移被推挤出视野
                    AnimatedContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                translationX = currentDrag
                            },
                        targetState = song,
                        transitionSpec = {
                            if (swipeDirection <= 0) {
                                slideInHorizontally { it / 2 } + fadeIn() togetherWith
                                    slideOutHorizontally { -it / 2 } + fadeOut()
                            } else {
                                slideInHorizontally { -it / 2 } + fadeIn() togetherWith
                                    slideOutHorizontally { it / 2 } + fadeOut()
                            }
                        },
                        contentKey = { it.filePath },
                        label = "textAnim"
                    ) { targetSong ->
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = targetSong.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = if (isFloatingBottomBar) 14.5.sp else 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 18.sp
                                ),
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = targetSong.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp
                                ),
                                color = artistColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 左滑时：从右侧推入"下一首"
                    Text(
                        text = "下一首",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = primaryColor,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .graphicsLayer {
                                translationX = switchThresholdPx + currentDrag
                                alpha = if (currentDrag < 0f) {
                                    (abs(currentDrag) / (switchThresholdPx * 0.6f)).coerceIn(0f, 1f)
                                } else 0f
                            }
                    )
                }

                // 播放/暂停按钮
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            context.performHapticFeedback(HapticFeedbackEffect.Click)
                            onPlayPause()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.action_pause) else stringResource(R.string.action_play),
                        modifier = Modifier.size(26.dp),
                        tint = titleColor
                    )
                }

                // 更多/播放列表按钮
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            context.performHapticFeedback(HapticFeedbackEffect.Click)
                            onMenuClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = stringResource(R.string.action_more),
                        modifier = Modifier.size(22.dp),
                        tint = titleColor
                    )
                }
            }

            // 进度条（绝对贴紧最底部边缘，裁切为胶囊圆角，无任何白边与间隙）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(2.5.dp)
                    .clip(barShape)
            ) {
                // 背景轨道
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f))
                )
                // 进度
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(primaryColor)
                )
            }
        }
    }
}

/**
 * 简化的专辑封面组件
 */
@Composable
private fun AlbumCoverSimple(
    filePath: String,
    albumArtUri: String? = null,
    size: Dp = 44.dp,
    cornerRadius: Dp = 10.dp
) {
    val model: Any = when {
        albumArtUri != null && (albumArtUri.startsWith("/") || albumArtUri.startsWith("file://")) ->
            File(albumArtUri.removePrefix("file://"))
        albumArtUri != null -> Uri.parse(albumArtUri)
        else -> File(filePath)
    }
    val painter = rememberAsyncImagePainter(model = model)

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .drawBehind {
                drawRect(Color.Transparent)
            },
        contentAlignment = Alignment.Center
    ) {
        // 底层：音乐图标
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // 上层：异步封面
        Image(
            painter = painter,
            contentDescription = "专辑封面",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}
