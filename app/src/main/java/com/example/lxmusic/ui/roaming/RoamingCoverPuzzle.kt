package com.example.lxmusic.ui.roaming

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * 拼图模式：把当前歌曲封面切成 3×3，点两块交换位置，拼好即完成。
 *
 * 移植自 CurrentMusicX 的 RoamingCoverPuzzle（GPL-3.0-only），图片加载换成本项目 Coil2。
 */
@Composable
fun RoamingCoverPuzzle(
    cover: String,
    songKey: Long?,
    modifier: Modifier = Modifier,
    showCaption: Boolean = true
) {
    val context = LocalContext.current
    val imageLoader = LocalImageLoader.current
    var bitmap by remember(cover) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(cover) { mutableStateOf(false) }
    var tiles by rememberSaveable(songKey) { mutableStateOf(listOf(4, 1, 6, 3, 8, 0, 7, 2, 5)) }
    var selected by remember(songKey) { mutableStateOf<Int?>(null) }
    val complete = tiles == (0..8).toList()

    LaunchedEffect(cover) {
        bitmap = null
        failed = false
        if (cover.isBlank()) {
            failed = true
            return@LaunchedEffect
        }
        val result = runCatching {
            imageLoader.execute(
                ImageRequest.Builder(context).data(cover).size(900).allowHardware(false).build()
            )
        }.getOrNull()
        bitmap = (result as? SuccessResult)?.drawable?.toBitmap()
        failed = bitmap == null
    }

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            val image = bitmap?.asImageBitmap()
            if (image == null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    tiles.chunked(3).forEachIndexed { row, values ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            values.forEachIndexed { column, tile ->
                                val index = row * 3 + column
                                Canvas(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(
                                            width = if (selected == index) 2.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        .clickable(enabled = !complete) {
                                            val first = selected
                                            if (first == null) {
                                                selected = index
                                            } else {
                                                tiles = tiles.toMutableList().apply {
                                                    val old = this[first]
                                                    this[first] = this[index]
                                                    this[index] = old
                                                }
                                                selected = null
                                            }
                                        }
                                ) {
                                    val side = minOf(image.width, image.height) / 3
                                    val offsetX = (image.width - side * 3) / 2 + (tile % 3) * side
                                    val offsetY = (image.height - side * 3) / 2 + (tile / 3) * side
                                    // 用 drawImage 的 srcOffset/srcSize 裁一块贴满当前格
                                    drawImage(
                                        image = image,
                                        srcOffset = IntOffset(offsetX, offsetY),
                                        srcSize = IntSize(side, side),
                                        dstOffset = IntOffset.Zero,
                                        dstSize = IntSize(size.width.toInt(), size.height.toInt())
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showCaption) {
            Text(
                text = when {
                    complete -> "拼好了，继续聆听这首歌"
                    failed -> "封面暂不可用，歌曲继续播放"
                    else -> "轻点两块碎片交换位置"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (complete && showCaption) {
            TextButton(onClick = {
                tiles = (0..8).shuffled()
                selected = null
            }) { Text("再拼一次") }
        }
    }
}
