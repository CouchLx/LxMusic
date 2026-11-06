package com.example.lxmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.lxmusic.ui.theme.ScreenAdapter
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 底栏条目：index 为 selectedTab 原始值（允许不连续，例如未激活模式 搜索=11） */
data class BottomBarTab(
    val index: Int,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val label: String
)

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
    // 底栏条目（激活/未激活两套）；传 null 用默认 首页/发现/我的
    tabs: List<BottomBarTab>? = null,
    blurEnabled: Boolean = false,
    isFloating: Boolean = true,
    liquidGlass: Boolean = false,
    // 有自定义壁纸才启用玻璃；纯色主题改用不透明表面色，避免糊成灰块
    hasCustomWallpaper: Boolean = false,
    backdrop: Backdrop? = null,
    navBarOpacity: Float = 1f,
    followThemeColor: Boolean = false,
    playerBarWhiteBlend: Float = 0.8f,
    // 原生底栏高度缩放（0.8~1.2，默认 1.0 = Material3 原生高度；悬浮底栏不受影响）
    bottomBarHeightScale: Float = 1f,
    currentSong: SongInfo? = null,
    isPlaying: Boolean = false,
    progress: StateFlow<PlayerProgress> = MutableStateFlow(PlayerProgress()),
    onPlayPause: () -> Unit = {},
    onNext: () -> Unit = {},
    onPlayerClick: () -> Unit = {}
) {
    val resolvedTabs = tabs ?: listOf(
        BottomBarTab(0, Icons.Filled.Home, Icons.Outlined.Home, stringResource(R.string.nav_home)),
        BottomBarTab(1, Icons.Filled.Search, Icons.Outlined.Search, stringResource(R.string.nav_discover)),
        BottomBarTab(2, Icons.Filled.Person, Icons.Outlined.Person, stringResource(R.string.nav_mine))
    )
    if (isFloating) {
        // 液态玻璃只要求「开关开 + 有 backdrop」：没有壁纸（纯色主题）也要有玻璃质感，
        // 否则开关看起来像没生效。solidSurface 仍按是否有壁纸决定回退样式。
        val glassEnabled = liquidGlass && backdrop != null
        val selectedPosition = resolvedTabs.indexOfFirst { it.index == selectedTabIndex }
            .coerceAtLeast(0)
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
                selectedIndex = selectedPosition,
                onSelected = { position ->
                    context.performHapticFeedback(HapticFeedbackEffect.Click)
                    resolvedTabs.getOrNull(position)?.let { onTabSelected(it.index) }
                },
                backdrop = backdrop,
                tabsCount = resolvedTabs.size,
                isBlurEnabled = glassEnabled,
                solidSurface = !hasCustomWallpaper,
                navBarOpacity = navBarOpacity,
                modifier = if (currentSong != null) Modifier.weight(1f) else Modifier.fillMaxWidth(0.92f)
            ) {
                resolvedTabs.forEach { tab ->
                    val isSelected = selectedTabIndex == tab.index
                    FloatingGlassBottomBarItem(
                        onClick = {
                            context.performHapticFeedback(HapticFeedbackEffect.Click)
                            onTabSelected(tab.index)
                        },
                        selected = isSelected,
                        label = tab.label
                    ) {
                        val tint = when {
                            liquidGlass -> MaterialTheme.colorScheme.onSurface
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        FloatingGlassBottomBarIcon(
                            selected = isSelected,
                            selectedIcon = tab.filledIcon,
                            unselectedIcon = tab.outlinedIcon,
                            tint = tint
                        )
                        FloatingGlassBottomBarLabel(
                            label = tab.label,
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
                        isBlurEnabled = glassEnabled,
                        navBarOpacity = navBarOpacity
                    )
                }
            }
        }
    } else {
        DefaultBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            tabs = resolvedTabs,
            blurEnabled = blurEnabled,
            navBarOpacity = navBarOpacity,
            bottomBarHeightScale = bottomBarHeightScale
        )
    }
}

// ==================== 默认导航栏（原生主题：全宽贴底，保持完全不变） ====================

@Composable
private fun DefaultBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<BottomBarTab>,
    blurEnabled: Boolean,
    navBarOpacity: Float = 1f,
    bottomBarHeightScale: Float = 1f
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

    // 高度调节：Material3 NavigationBar 内部为 defaultMinSize(80dp)（非硬编码高度），
    // 外层固定高度 = 80dp × 系数 + 系统导航条 inset，100% 时与原生行为逐像素一致。
    // 缩放只作用于底栏内容区，系统导航条安全区不变；icon/文字由 M3 自身在约束内居中，不会裁切。
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val heightScale = bottomBarHeightScale.coerceIn(
        ScreenAdapter.MIN_BOTTOM_BAR_HEIGHT_SCALE,
        ScreenAdapter.MAX_BOTTOM_BAR_HEIGHT_SCALE
    )
    // 与迷你播放条跟随动画同时长（300ms），保证调节过程中播放条始终与底栏贴紧
    val animatedBarHeight by animateDpAsState(
        targetValue = 80.dp * heightScale + navBarInset,
        animationSpec = tween(durationMillis = 300),
        label = "nativeBottomBarHeight"
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
                tonalElevation = 0.dp,
                modifier = Modifier.height(animatedBarHeight)
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTabIndex == tab.index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            context.performHapticFeedback(HapticFeedbackEffect.Click)
                            onTabSelected(tab.index)
                        },
                        icon = {
                            Icon(
                                if (isSelected) tab.filledIcon else tab.outlinedIcon,
                                contentDescription = null
                            )
                        },
                        label = { Text(tab.label) },
                        alwaysShowLabel = true,
                        colors = itemColors
                    )
                }
            }
        }
    }
}
