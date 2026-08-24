package com.example.lxmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.lxmusic.PlayerProgress
import com.example.lxmusic.R
import com.example.lxmusic.model.SongInfo
import com.example.lxmusic.ui.components.bottombar.FloatingGlassBottomBar
import com.example.lxmusic.ui.components.bottombar.FloatingGlassBottomBarIcon
import com.example.lxmusic.ui.components.bottombar.FloatingGlassBottomBarItem
import com.example.lxmusic.ui.components.bottombar.FloatingGlassBottomBarLabel
import com.example.lxmusic.ui.components.bottombar.FloatingGlassPlayerCapsule
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 底栏入口组件。
 *
 * isFloating = false → 原始 Material NavigationBar（原生主题，保持不变）
 * isFloating = true  → NPatch 1:1 悬浮分页底栏 + 单行微型播放器胶囊
 * liquidGlass = true → NPatch 1:1 双层采样透镜折射 + AGSL 交互高光
 */
@Composable
fun FloatingBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    blurEnabled: Boolean = false,
    isFloating: Boolean = true,
    liquidGlass: Boolean = false,
    backdrop: Backdrop? = null,
    navBarOpacity: Float = 1f,
    followThemeColor: Boolean = false,
    playerBarWhiteBlend: Float = 0.8f,
    currentSong: SongInfo? = null,
    isPlaying: Boolean = false,
    progress: StateFlow<PlayerProgress> = MutableStateFlow(PlayerProgress()),
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPlayerClick: () -> Unit = {}
) {
    if (isFloating) {
        val tabs = listOf(
            Triple(0, Pair(Icons.Filled.Home, Icons.Outlined.Home), stringResource(R.string.nav_home)),
            Triple(1, Pair(Icons.Filled.Search, Icons.Outlined.Search), stringResource(R.string.nav_discover)),
            Triple(2, Pair(Icons.Filled.Person, Icons.Outlined.Person), stringResource(R.string.nav_mine))
        )
        val context = LocalContext.current
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：NPatch 1:1 悬浮分页底栏
            FloatingGlassBottomBar(
                selectedIndex = selectedTabIndex.coerceIn(0, 2),
                onSelected = { index ->
                    context.performHapticFeedback(HapticFeedbackEffect.Click)
                    onTabSelected(index)
                },
                backdrop = backdrop,
                tabsCount = tabs.size,
                isBlurEnabled = liquidGlass,
                navBarOpacity = navBarOpacity,
                modifier = if (currentSong != null) Modifier.weight(1f) else Modifier.fillMaxWidth(0.92f)
            ) {
                tabs.forEach { (index, icons, label) ->
                    val isSelected = selectedTabIndex == index
                    FloatingGlassBottomBarItem(
                        onClick = {
                            context.performHapticFeedback(HapticFeedbackEffect.Click)
                            onTabSelected(index)
                        },
                        selected = isSelected,
                        label = label
                    ) {
                        val tint = when {
                            liquidGlass -> MaterialTheme.colorScheme.onSurface
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        FloatingGlassBottomBarIcon(
                            selected = isSelected,
                            selectedIcon = icons.first,
                            unselectedIcon = icons.second,
                            tint = tint
                        )
                        FloatingGlassBottomBarLabel(
                            label = label,
                            color = tint
                        )
                    }
                }
            }

            // 右侧：紧凑圆形/胶囊迷你播放器
            AnimatedVisibility(
                visible = currentSong != null,
                enter = scaleIn(tween(260)) + fadeIn(tween(220)),
                exit = scaleOut(tween(200)) + fadeOut(tween(150))
            ) {
                currentSong?.let { song ->
                    FloatingGlassPlayerCapsule(
                        song = song,
                        isPlaying = isPlaying,
                        progress = progress,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onClick = onPlayerClick,
                        backdrop = backdrop,
                        isBlurEnabled = liquidGlass,
                        navBarOpacity = navBarOpacity
                    )
                }
            }
        }
    } else {
        DefaultBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            blurEnabled = blurEnabled,
            navBarOpacity = navBarOpacity
        )
    }
}

// ==================== 默认导航栏（原生主题：全宽贴底，保持完全不变） ====================

@Composable
private fun DefaultBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    blurEnabled: Boolean,
    navBarOpacity: Float = 1f
) {
    val barColor = MaterialTheme.colorScheme.background.copy(alpha = navBarOpacity.coerceIn(0f, 1f))
    val barShape = RoundedCornerShape(0.dp)
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = barShape,
            color = barColor,
            tonalElevation = 0.dp
        ) {
            val context = LocalContext.current
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = {
                        context.performHapticFeedback(HapticFeedbackEffect.Click)
                        onTabSelected(0)
                    },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text(stringResource(R.string.nav_home)) },
                    alwaysShowLabel = true,
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        context.performHapticFeedback(HapticFeedbackEffect.Click)
                        onTabSelected(1)
                    },
                    icon = { Icon(Icons.Default.Search, null) },
                    label = { Text(stringResource(R.string.nav_discover)) },
                    alwaysShowLabel = true,
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 2,
                    onClick = {
                        context.performHapticFeedback(HapticFeedbackEffect.Click)
                        onTabSelected(2)
                    },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text(stringResource(R.string.nav_mine)) },
                    alwaysShowLabel = true,
                    colors = itemColors
                )
            }
        }
    }
}
