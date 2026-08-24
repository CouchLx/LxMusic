package com.example.lxmusic.ui.components.wallpaper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 安全解码并自动校正 EXIF 旋转角度的 Bitmap
 */
suspend fun decodeBitmapWithRotation(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
    return withContext(Dispatchers.IO) {
        try {
            // 1. 读取尺寸
            var input: InputStream? = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            // 计算采样率
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            // 2. 解码实际 Bitmap
            input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val rawBitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
            input.close()
            if (rawBitmap == null) return@withContext null

            // 3. 读取 EXIF 角度并旋转
            val exifInput = context.contentResolver.openInputStream(uri)
            val rotation = if (exifInput != null) {
                val exif = ExifInterface(exifInput)
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                exifInput.close()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } else 0f

            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                if (rotated != rawBitmap) {
                    rawBitmap.recycle()
                }
                rotated
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            android.util.Log.e("WallpaperCropper", "decodeBitmapWithRotation error", e)
            null
        }
    }
}

/**
 * 手机屏幕比例全屏图片裁剪与预览对话框
 */
@Composable
fun WallpaperCropDialog(
    imageUri: Uri,
    onDismiss: () -> Unit,
    onCropSuccess: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    // 缩放与平移状态
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // 手机屏幕标准高宽比（默认 9 : 19.5）
    val phoneAspectRatio = 9f / 19.5f

    LaunchedEffect(imageUri) {
        isLoading = true
        sourceBitmap = decodeBitmapWithRotation(context, imageUri)
        isLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isSaving,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                val screenH = maxHeight.value

                if (isLoading || sourceBitmap == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    val bmp = sourceBitmap!!

                    // 计算裁剪框在屏幕中央的尺寸
                    val frameHeightDp = (screenH * 0.74f).coerceAtMost(screenH - 140f)
                    val frameWidthDp = frameHeightDp * phoneAspectRatio
                    val density = LocalDensity.current

                    val frameWidthPx = with(density) { frameWidthDp.dp.toPx() }
                    val frameHeightPx = with(density) { frameHeightDp.dp.toPx() }

                    // 初始 fit-crop 缩放
                    val baseScale = max(frameWidthPx / bmp.width, frameHeightPx / bmp.height)

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 顶部操作栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                enabled = !isSaving
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "取消", tint = Color.White)
                            }

                            Text(
                                text = "调整壁纸预览",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            IconButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                enabled = !isSaving
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "重置", tint = Color.White.copy(0.85f))
                            }
                        }

                        // 中间裁剪视窗区域
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            // 裁剪框
                            Box(
                                modifier = Modifier
                                    .size(frameWidthDp.dp, frameHeightDp.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .pointerInput(Unit) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                            // 计算最大允许平移边界
                                            val curDrawnW = bmp.width * baseScale * scale
                                            val curDrawnH = bmp.height * baseScale * scale
                                            val maxOffsetX = max(0f, (curDrawnW - frameWidthPx) / 2f)
                                            val maxOffsetY = max(0f, (curDrawnH - frameHeightPx) / 2f)

                                            val newX = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                            val newY = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            offset = Offset(newX, newY)
                                        }
                                    }
                            ) {
                                // 实际图片渲染 Canvas
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val totalScale = baseScale * scale
                                    val drawnW = bmp.width * totalScale
                                    val drawnH = bmp.height * totalScale

                                    val left = (size.width - drawnW) / 2f + offset.x
                                    val top = (size.height - drawnH) / 2f + offset.y

                                    drawImage(
                                        image = bmp.asImageBitmap(),
                                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                        dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt())
                                    )
                                }

                                // 裁剪框边框（高亮白色细线）
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.65f),
                                        size = size,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx()),
                                        style = Stroke(width = 2.5.dp.toPx())
                                    )
                                }
                            }
                        }

                        // 底部说明文字
                        Text(
                            text = "双指缩放 · 拖拽调整最佳画面",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // 底部确认按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                )
                            ) {
                                Text("取消", fontSize = 15.sp)
                            }

                            Button(
                                onClick = {
                                    if (isSaving) return@Button
                                    isSaving = true
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val totalScale = baseScale * scale
                                            val drawnW = bmp.width * totalScale
                                            val drawnH = bmp.height * totalScale

                                            val relX = (drawnW - frameWidthPx) / 2f - offset.x
                                            val relY = (drawnH - frameHeightPx) / 2f - offset.y

                                            val cropX = (relX / totalScale).coerceIn(0f, (bmp.width - 1).toFloat())
                                            val cropY = (relY / totalScale).coerceIn(0f, (bmp.height - 1).toFloat())
                                            val cropW = (frameWidthPx / totalScale).coerceAtMost(bmp.width - cropX)
                                            val cropH = (frameHeightPx / totalScale).coerceAtMost(bmp.height - cropY)

                                            val croppedBmp = Bitmap.createBitmap(
                                                bmp,
                                                cropX.roundToInt(),
                                                cropY.roundToInt(),
                                                cropW.roundToInt().coerceAtLeast(1),
                                                cropH.roundToInt().coerceAtLeast(1)
                                            )

                                            val wallDir = File(context.filesDir, "wallpapers")
                                            if (!wallDir.exists()) wallDir.mkdirs()

                                            val outFile = File(wallDir, "wall_${System.currentTimeMillis()}.jpg")
                                            FileOutputStream(outFile).use { fos ->
                                                croppedBmp.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                                fos.flush()
                                            }

                                            if (croppedBmp != bmp) {
                                                croppedBmp.recycle()
                                            }

                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                                onCropSuccess(outFile)
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("WallpaperCropper", "Crop and save failed", e)
                                            withContext(Dispatchers.Main) {
                                                isSaving = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("设为壁纸", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
