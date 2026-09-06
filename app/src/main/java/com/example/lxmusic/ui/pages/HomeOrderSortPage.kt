package com.example.lxmusic.ui.pages

import android.content.SharedPreferences
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 首页排序页（设置 → 通用设置 → 首页排序）：
 * 顶部推荐栏 6 张卡 + 下面 8 个歌曲分区，长按右侧把手拖动换位，松手自动保存，
 * 返回首页即时生效。逻辑照抄管理歌单页（PlaylistManagerPage）的拖动排序。
 */
@Composable
fun HomeOrderSortPage(
    settingsPrefs: SharedPreferences
) {
    Column {
        Text(
            text = "首页排序",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "长按右侧把手拖动调整首页显示顺序，松手自动保存，返回即生效",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        HomeOrderGroup(
            title = "顶部推荐栏",
            prefKey = "home_top_order",
            defaults = HOME_TOP_IDS,
            titles = HOME_TOP_TITLES,
            settingsPrefs = settingsPrefs
        )

        Spacer(modifier = Modifier.height(16.dp))

        HomeOrderGroup(
            title = "下面歌曲推荐",
            prefKey = "home_section_order",
            defaults = HOME_SECTION_IDS,
            titles = HOME_SECTION_TITLES,
            settingsPrefs = settingsPrefs
        )
    }
}

@Composable
private fun HomeOrderGroup(
    title: String,
    prefKey: String,
    defaults: List<String>,
    titles: Map<String, String>,
    settingsPrefs: SharedPreferences
) {
    var ids by remember(prefKey) {
        mutableStateOf(homeOrderedIds(settingsPrefs.getString(prefKey, null), defaults))
    }

    fun save(list: List<String>) {
        settingsPrefs.edit().putString(prefKey, list.joinToString(",")).apply()
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromId = from.key as? String ?: return@rememberReorderableLazyListState
            val toId = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIdx = ids.indexOf(fromId)
            val toIdx = ids.indexOf(toId)
            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                ids = ids.toMutableList().apply { add(toIdx, removeAt(fromIdx)) }
                save(ids)
            }
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                ids = defaults
                save(ids)
            }
        ) {
            Text("恢复默认")
        }
    }
    Spacer(modifier = Modifier.height(6.dp))

    // 定高 + 禁止滚动（外层设置页本身可滚）：6~8 行完全展示，拖动手势不受影响
    LazyColumn(
        state = listState,
        modifier = Modifier.height((ids.size * 64).dp),
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ids, key = { it }) { id ->
            ReorderableItem(
                state = reorderableState,
                key = id
            ) { isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "orderElevation"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.02f else 1.0f,
                    label = "orderScale"
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(scale)
                        .shadow(elevation, RoundedCornerShape(12.dp))
                        .longPressDraggableHandle(onDragStarted = {}, onDragStopped = {}),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 序号
                        val index = ids.indexOf(id) + 1
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$index",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isDragging) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = titles[id] ?: id,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "长按拖动排序",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
