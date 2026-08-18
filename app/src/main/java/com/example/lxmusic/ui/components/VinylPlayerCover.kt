package com.example.lxmusic.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.example.lxmusic.model.SongInfo
import java.io.File

/**
 * 唱片播放器模块化组件（支持三个部分独立开启、自由搭配，统一比例与坐标体系）：
 * 1. 【底板 (VinylBaseCard)】：白玉底板 + 右下角徽标（切歌时恒定静态，绝不重载重绘）。
 * 2. 【指针 (VinylTonearm)】：金属唱臂、配重块、转轴与右上角凹槽（切歌时恒定静态，不随切歌重载）。
 * 3. 【黑胶唱片 (VinylDisc)】：仅黑胶唱片执行平滑的物理飞出/飞入动效：
 *    - 下一首：当前唱片以中速向左飞出屏幕边缘，直接露出下方已准备好的下一首唱片并放大。
 *    - 上一首：上一首唱片从左侧飞入并覆盖当前唱片。
 */
@Composable
fun VinylPlayerCover(
    modifier: Modifier = Modifier,
    cardWidth: Dp = 340.dp,
    song: SongInfo?,
    isPlaying: Boolean,
    rotationAngle: Float,
    isNextSong: Boolean = true,
    showDisc: Boolean = true,
    showPointer: Boolean = true,
    showBase: Boolean = true
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 1. 【底板层】：恒定静态白玉底座卡片（位于最底层，保持 1.10f 比例居中）
        if (showBase) {
            Box(
                modifier = Modifier
                    .requiredWidth(cardWidth)
                    .fillMaxHeight()
                    .aspectRatio(1.10f),
                contentAlignment = Alignment.Center
            ) {
                VinylBaseCard(modifier = Modifier.fillMaxSize())
            }
        }

        // 2. 【唱片层】：跨越全屏全画幅（fillMaxSize），动画全程无任何局部容器边界阻隔，完整划过卡片与左侧屏幕边缘飞出
        val smoothGlideEasing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)
        val glideDurationMs = 700

        AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            targetState = song,
            transitionSpec = {
                if (isNextSong) {
                    // 下一首：
                    // - 当前唱片（顶层）：以丝滑速率完整向左划出卡片与屏幕左侧，划行全程实体清晰可见，无任何边界裁切
                    // - 底层新唱片（底层）：默认处于缩小一圈状态（scale = 0.85f），在旧唱片划出后自然过渡平滑放大至 1.0f 正常大小
                    (scaleIn(
                        animationSpec = tween(durationMillis = 520, delayMillis = 180, easing = FastOutSlowInEasing),
                        initialScale = 0.85f
                    ) + fadeIn(animationSpec = tween(120)) togetherWith
                            slideOutHorizontally(
                                animationSpec = tween(durationMillis = glideDurationMs, easing = smoothGlideEasing)
                            ) { fullWidth -> -fullWidth } +
                            fadeOut(animationSpec = tween(durationMillis = 100, delayMillis = glideDurationMs - 100)))
                        .apply {
                            targetContentZIndex = -1f
                        }
                } else {
                    // 上一首：
                    // - 上一首唱片（顶层）：正常尺寸（scale 1.0f）从屏幕左侧滑入覆盖当前唱片
                    // - 当前唱片（底层）：平滑淡出，伴随轻微缩小到 0.88f
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = glideDurationMs, easing = smoothGlideEasing)
                    ) { fullWidth -> -fullWidth } + fadeIn(animationSpec = tween(120)) togetherWith
                            scaleOut(
                                animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
                                targetScale = 0.88f
                            ) + fadeOut(animationSpec = tween(durationMillis = 150, delayMillis = glideDurationMs - 150)))
                        .apply {
                            targetContentZIndex = 1f
                        }
                }
            },
            contentKey = { it?.filePath ?: "" },
            label = "discFlyAnim"
        ) { targetSong ->
            val targetModel: Any? = when {
                targetSong == null -> null
                targetSong.albumArtUri != null && (targetSong.albumArtUri.startsWith("/") || targetSong.albumArtUri.startsWith("file://")) ->
                    File(targetSong.albumArtUri.removePrefix("file://"))
                targetSong.albumArtUri != null -> Uri.parse(targetSong.albumArtUri)
                else -> File(targetSong.filePath)
            }
            val targetPainter = rememberAsyncImagePainter(model = targetModel)

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .requiredWidth(cardWidth)
                        .fillMaxHeight()
                        .aspectRatio(1.10f),
                    contentAlignment = Alignment.Center
                ) {
                    VinylDisc(
                        modifier = Modifier
                            .fillMaxHeight(0.92f)
                            .aspectRatio(1f),
                        albumPainter = targetPainter,
                        rotationAngle = rotationAngle,
                        showDiscTexture = showDisc
                    )
                }
            }
        }

        // 3. 【指针层】：恒定静态指针总成（必须位于最顶层，针尖自然搭在黑胶唱片音轨之上，绝不被唱片遮挡）
        if (showPointer) {
            Box(
                modifier = Modifier
                    .requiredWidth(cardWidth)
                    .fillMaxHeight()
                    .aspectRatio(1.10f),
                contentAlignment = Alignment.Center
            ) {
                VinylTonearm(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-26).dp),
                    isPlaying = isPlaying
                )
            }
        }
    }
}

/**
 * 1. 【底板组件 (VinylBaseCard)】：
 * - 纯净白玉唱机底座卡片（aspectRatio 1.10，32dp 大圆角、立体投影、金属白边）。
 * - 右下角内凹圆形徽标（经典黑色音乐图标）。
 */
@Composable
fun VinylBaseCard(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8F9FA),
                        Color(0xFFF1F3F7)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(32.dp))
    ) {
        // 右下角精致内凹圆形指示标（黑色图标）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 14.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFEBF0F6))
                .border(1.dp, Color(0xFFD8DEE8), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF1E1E22) // 黑色图标
            )
        }
    }
}

/**
 * 2. 【黑胶唱片组件 (VinylDisc)】：
 * - showDiscTexture == true：完整黑胶唱片盘（24 圈精密微音轨、扇形反光、中心专辑封面与防露白黑边）。
 * - showDiscTexture == false：纯净圆形专辑封面。
 */
@Composable
fun VinylDisc(
    modifier: Modifier = Modifier,
    albumPainter: Painter?,
    rotationAngle: Float,
    showDiscTexture: Boolean = true
) {
    val coverFraction = if (showDiscTexture) 0.64f else 1.0f

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(
                elevation = if (showDiscTexture) 14.dp else 8.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = if (showDiscTexture) 0.45f else 0.25f),
                spotColor = Color.Black.copy(alpha = if (showDiscTexture) 0.55f else 0.35f)
            )
            .graphicsLayer { rotationZ = rotationAngle },
        contentAlignment = Alignment.Center
    ) {
        if (showDiscTexture) {
            // 黑胶外盘 Canvas 绘制：深邃纯黑底色 + 24 圈精密微音轨 + 柔顺扇形高光 + 防露白遮罩
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val outerRadius = size.minDimension / 2f
                val innerRadius = outerRadius * coverFraction

                // 1. 底盘黑胶深邃纯黑背景
                drawCircle(
                    color = Color(0xFF0A0A0D),
                    radius = outerRadius,
                    center = center
                )

                // 2. 拟真幽微密集黑胶微音轨
                val trackStartRadius = innerRadius + 5.dp.toPx()
                val trackSpan = outerRadius - trackStartRadius
                val trackCount = 24
                for (i in 0 until trackCount) {
                    val fraction = (i + 1).toFloat() / (trackCount + 1).toFloat()
                    val r = trackStartRadius + trackSpan * fraction
                    val alpha = when (i % 4) {
                        0 -> 0.055f
                        1 -> 0.025f
                        2 -> 0.040f
                        else -> 0.015f
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = r,
                        center = center,
                        style = Stroke(width = 0.6.dp.toPx())
                    )
                }

                // 3. 经典黑胶柔润主扇形高光
                val softHighlightBrush = Brush.sweepGradient(
                    0.00f to Color.Transparent,
                    0.07f to Color.White.copy(alpha = 0.05f),
                    0.14f to Color.White.copy(alpha = 0.15f),
                    0.21f to Color.White.copy(alpha = 0.05f),
                    0.28f to Color.Transparent,
                    0.50f to Color.Transparent,
                    0.57f to Color.White.copy(alpha = 0.05f),
                    0.64f to Color.White.copy(alpha = 0.15f),
                    0.71f to Color.White.copy(alpha = 0.05f),
                    0.78f to Color.Transparent,
                    1.00f to Color.Transparent,
                    center = center
                )
                drawCircle(
                    brush = softHighlightBrush,
                    radius = outerRadius,
                    center = center
                )

                // 4. 次级交叉微柔光区
                val crossHighlightBrush = Brush.sweepGradient(
                    0.32f to Color.Transparent,
                    0.39f to Color.White.copy(alpha = 0.04f),
                    0.46f to Color.Transparent,
                    0.82f to Color.Transparent,
                    0.89f to Color.White.copy(alpha = 0.04f),
                    0.96f to Color.Transparent,
                    center = center
                )
                drawCircle(
                    brush = crossHighlightBrush,
                    radius = outerRadius,
                    center = center
                )

                // 5. 外边缘立体光泽凹槽与微高光外圈
                drawCircle(
                    color = Color(0xFF1E1E24),
                    radius = outerRadius - 1f,
                    center = center,
                    style = Stroke(width = 2.0f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = outerRadius - 2.0f,
                    center = center,
                    style = Stroke(width = 0.8f)
                )

                // 6. 纯黑防露白遮罩层（彻底抹除中心扇形高光残留）
                drawCircle(
                    color = Color(0xFF0A0A0D),
                    radius = innerRadius + 4.dp.toPx(),
                    center = center
                )
                drawCircle(
                    color = Color.Black,
                    radius = innerRadius + 2.dp.toPx(),
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }

        // 唱片中心圆形封面
        Box(
            modifier = Modifier
                .fillMaxSize(coverFraction)
                .clip(CircleShape)
                .background(Color(0xFF0A0A0D)),
            contentAlignment = Alignment.Center
        ) {
            if (albumPainter != null) {
                Image(
                    painter = albumPainter,
                    contentDescription = "专辑封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (albumPainter == null || (albumPainter as? AsyncImagePainter)?.state !is AsyncImagePainter.State.Success) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }

            if (showDiscTexture) {
                // 封面外边缘黑边过渡环（消除接缝露白）
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Color.Black.copy(alpha = 0.85f), CircleShape)
                        .border(1.dp, Color(0xFF0A0A0D), CircleShape)
                )
            }
        }
    }
}

/**
 * 3. 【指针组件 (VinylTonearm)】：
 * - 包含转轴底座凹槽（即便脱离底板也完整呈现）。
 * - 亮银镀铬金属唱臂、3D 车削不锈钢配重块、平头顶针、白玉唱头与 90 度金属提手。
 * - 白玉主转轴【在凹槽正中心绝对固定，零位移零漂移，严密覆盖金属连杆根部】。
 * - 播放旋转角度为 11°。
 */
@Composable
fun VinylTonearm(
    modifier: Modifier = Modifier,
    isPlaying: Boolean
) {
    val armAngle by animateFloatAsState(
        targetValue = if (isPlaying) 11f else 0f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "tonearmAngle"
    )

    Box(modifier = modifier.size(width = 100.dp, height = 360.dp)) {
        val pivotX = 68.dp
        val pivotY = 58.dp

        Canvas(modifier = Modifier.fillMaxSize()) {
            val px = pivotX.toPx()
            val py = pivotY.toPx()
            val pivotOffset = Offset(px, py)

            // =========================================================================
            // 0. 转轴底座凹槽（宽度 38dp，高度微增至 48dp，修长适中，转轴唱臂保持原位）
            // =========================================================================
            val recessWidth = 38.dp.toPx()
            val recessHeight = 48.dp.toPx()
            val recessTopLeft = Offset(px - recessWidth / 2f, py - 22.dp.toPx())
            val recessRadius = CornerRadius(19.dp.toPx(), 19.dp.toPx())

            drawRoundRect(
                color = Color(0xFFE5E9F1),
                topLeft = recessTopLeft,
                size = Size(recessWidth, recessHeight),
                cornerRadius = recessRadius
            )
            drawRoundRect(
                color = Color(0xFFD6DBE5),
                topLeft = recessTopLeft,
                size = Size(recessWidth, recessHeight),
                cornerRadius = recessRadius,
                style = Stroke(width = 0.8.dp.toPx())
            )

            // =========================================================================
            // 1. 动态金属连杆总成：精确以 pivotOffset (pivotX, pivotY) 为中心旋转
            // =========================================================================
            rotate(degrees = armAngle, pivot = pivotOffset) {
                val cardTopEdge = 26.dp.toPx()
                val barrelHeight = 16.dp.toPx()
                val barrelWidth = 22.dp.toPx()
                val barrelTop = cardTopEdge - barrelHeight
                val left = px - barrelWidth / 2f
                val right = px + barrelWidth / 2f
                val pinHeight = 5.5.dp.toPx()

                // 尾段金属块底层立体柔和阴影
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.16f),
                    topLeft = Offset(left + 1.5.dp.toPx(), barrelTop + 2.dp.toPx()),
                    size = Size(barrelWidth, barrelHeight),
                    cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                )

                // ① 细平头金属顶针（亮银镀铬，与金属管完全同源质感）
                val pinStart = Offset(px, barrelTop - pinHeight)
                val pinEnd = Offset(px, barrelTop)
                drawLine(
                    color = Color(0xFF656870),
                    start = pinStart,
                    end = pinEnd,
                    strokeWidth = 4.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF9497A0), Color(0xFFECEFF6), Color(0xFFFFFFFF), Color(0xFF888B94)),
                        startX = px - 2.2.dp.toPx(),
                        endX = px + 2.2.dp.toPx()
                    ),
                    start = pinStart,
                    end = pinEnd,
                    strokeWidth = 3.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = pinStart,
                    end = pinEnd,
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // ② 尾段金属配重块（亮银镀铬高精 3D 圆柱体）
                val brightChromeBrush = Brush.horizontalGradient(
                    0.00f to Color(0xFF72757E),
                    0.15f to Color(0xFFA2A6B0),
                    0.35f to Color(0xFFF2F5FA),
                    0.50f to Color(0xFFFFFFFF),
                    0.65f to Color(0xFFDCE0E9),
                    0.85f to Color(0xFF8E929C),
                    1.00f to Color(0xFF686B73),
                    startX = left,
                    endX = right
                )
                drawRoundRect(
                    brush = brightChromeBrush,
                    topLeft = Offset(left, barrelTop),
                    size = Size(barrelWidth, barrelHeight),
                    cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
                )

                // 正中央纵向镜面纯白反光带
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.0f),
                            Color.White.copy(alpha = 0.75f),
                            Color.White.copy(alpha = 0.0f)
                        ),
                        startX = px - 3.5.dp.toPx(),
                        endX = px + 3.5.dp.toPx()
                    ),
                    topLeft = Offset(left, barrelTop),
                    size = Size(barrelWidth, barrelHeight)
                )

                // 顶部车削微台阶环
                val topCollarBrush = Brush.horizontalGradient(
                    0.00f to Color(0xFF7A7D86),
                    0.35f to Color(0xFFD0D4DE),
                    0.50f to Color(0xFFF5F7FB),
                    0.70f to Color(0xFFA6ABB5),
                    1.00f to Color(0xFF70737C),
                    startX = left,
                    endX = right
                )
                drawRoundRect(
                    brush = topCollarBrush,
                    topLeft = Offset(left, barrelTop),
                    size = Size(barrelWidth, 2.8.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
                drawRect(
                    color = Color(0xFF5E616A).copy(alpha = 0.7f),
                    topLeft = Offset(left, barrelTop + 2.8.dp.toPx()),
                    size = Size(barrelWidth, 0.7.dp.toPx())
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(left, barrelTop + 3.5.dp.toPx()),
                    size = Size(barrelWidth, 0.5.dp.toPx())
                )

                // 底部车削凹槽刻线
                val grooveY = cardTopEdge - 4.dp.toPx()
                drawRect(
                    color = Color(0xFF52555E).copy(alpha = 0.75f),
                    topLeft = Offset(left, grooveY),
                    size = Size(barrelWidth, 0.8.dp.toPx())
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.7f),
                    topLeft = Offset(left, grooveY + 0.8.dp.toPx()),
                    size = Size(barrelWidth, 0.6.dp.toPx())
                )

                // 顶端与底端 3D 倒角高光扫光
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent),
                        startX = left,
                        endX = right
                    ),
                    topLeft = Offset(left, barrelTop),
                    size = Size(barrelWidth, 0.7.dp.toPx())
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startX = left,
                        endX = right
                    ),
                    topLeft = Offset(left, cardTopEdge - 0.7.dp.toPx()),
                    size = Size(barrelWidth, 0.7.dp.toPx())
                )

                // 金属块极细柔和边缘勾勒
                drawRoundRect(
                    color = Color(0xFF50525A).copy(alpha = 0.5f),
                    topLeft = Offset(left, barrelTop),
                    size = Size(barrelWidth, barrelHeight),
                    cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx()),
                    style = Stroke(width = 0.5.dp.toPx())
                )

                // ③ 上部连接金属立柱
                val shaftStart = Offset(px, cardTopEdge)
                val shaftEnd = Offset(px, py)
                drawLine(
                    color = Color(0xFF656870),
                    start = shaftStart,
                    end = shaftEnd,
                    strokeWidth = 4.8.dp.toPx()
                )
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF9497A0), Color(0xFFECEFF6), Color(0xFFFFFFFF), Color(0xFF888B94)),
                        startX = px - 2.4.dp.toPx(),
                        endX = px + 2.4.dp.toPx()
                    ),
                    start = shaftStart,
                    end = shaftEnd,
                    strokeWidth = 4.0.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.95f),
                    start = shaftStart,
                    end = shaftEnd,
                    strokeWidth = 1.2.dp.toPx()
                )

                // ④ 下部唱臂管与白玉唱头
                val headX = px - 24.dp.toPx()
                val headY = py + 212.dp.toPx()

                val armPath = Path().apply {
                    moveTo(px, py)
                    lineTo(px, py + 144.dp.toPx())
                    quadraticTo(
                        px, py + 164.dp.toPx(),
                        px - 8.dp.toPx(), py + 180.dp.toPx()
                    )
                    lineTo(headX, headY)
                }

                val shadowPath = Path().apply {
                    addPath(armPath, Offset(1.5.dp.toPx(), 2.dp.toPx()))
                }
                drawPath(
                    path = shadowPath,
                    color = Color.Black.copy(alpha = 0.16f),
                    style = Stroke(width = 5.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawPath(
                    path = armPath,
                    color = Color(0xFF656870),
                    style = Stroke(width = 4.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawPath(
                    path = armPath,
                    brush = Brush.linearGradient(
                        listOf(
                            Color(0xFF9497A0),
                            Color(0xFFECEFF6),
                            Color(0xFFA8ACB6),
                            Color(0xFFFFFFFF),
                            Color(0xFF888B94)
                        ),
                        start = Offset(px - 10.dp.toPx(), py),
                        end = Offset(headX + 15.dp.toPx(), headY)
                    ),
                    style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawPath(
                    path = armPath,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.95f),
                            Color.White.copy(alpha = 0.70f)
                        ),
                        start = Offset(px, py),
                        end = Offset(headX, headY)
                    ),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // ⑤ 白玉胶囊唱头与垂直金属提手
                rotate(degrees = 26f, pivot = Offset(headX, headY)) {
                    val needleY = headY + 12.dp.toPx()
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFD2D6DE), Color(0xFF7A7D85)),
                            startX = headX + 7.dp.toPx(),
                            endX = headX + 24.dp.toPx()
                        ),
                        start = Offset(headX + 7.dp.toPx(), needleY),
                        end = Offset(headX + 24.dp.toPx(), needleY),
                        strokeWidth = 2.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.1.dp.toPx(),
                        center = Offset(headX + 24.dp.toPx(), needleY)
                    )

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.18f),
                        topLeft = Offset(headX - 8.dp.toPx() + 1.5f, headY - 4.dp.toPx() + 2f),
                        size = Size(16.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFFFFFFF), Color(0xFFF9FAFC), Color(0xFFECEFF5)),
                            startY = headY - 4.dp.toPx(),
                            endY = headY + 22.dp.toPx()
                        ),
                        topLeft = Offset(headX - 8.dp.toPx(), headY - 4.dp.toPx()),
                        size = Size(16.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFD6DAE2),
                        topLeft = Offset(headX - 8.dp.toPx(), headY - 4.dp.toPx()),
                        size = Size(16.dp.toPx(), 26.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = 0.6.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0xFFCCD1DC),
                        radius = 3.2.dp.toPx(),
                        center = Offset(headX, headY + 14.dp.toPx()),
                        style = Stroke(width = 1.1.dp.toPx())
                    )
                }
            }

            // =========================================================================
            // 2. 静态白玉主转轴圆盘：位于 pivotOffset，绝对静止、严密覆盖金属管根部
            // =========================================================================
            drawCircle(
                color = Color.Black.copy(alpha = 0.12f),
                radius = 15.dp.toPx(),
                center = Offset(px + 1.dp.toPx(), py + 1.5.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF9FAFC), Color(0xFFECEFF5)),
                    center = Offset(px - 2.dp.toPx(), py - 2.dp.toPx()),
                    radius = 14.dp.toPx()
                ),
                radius = 14.dp.toPx(),
                center = pivotOffset
            )
            drawCircle(
                color = Color(0xFFD6DAE2),
                radius = 14.dp.toPx(),
                center = pivotOffset,
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFD0D5E0),
                radius = 9.dp.toPx(),
                center = pivotOffset,
                style = Stroke(width = 0.8.dp.toPx())
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FB), Color(0xFFEFF1F6)),
                    center = Offset(px - 1.dp.toPx(), py - 1.dp.toPx()),
                    radius = 8.dp.toPx()
                ),
                radius = 8.dp.toPx(),
                center = pivotOffset
            )
        }
    }
}
