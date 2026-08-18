package com.example.lxmusic.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
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
import com.example.lxmusic.R
import com.example.lxmusic.ui.components.bottombar.KernelSuAlignedBottomBar
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow

/**
 * 底栏入口组件。
 *
 * isFloating = false → 原始 Material NavigationBar（和改之前一模一样）
 * isFloating = true  → 胶囊底栏（BiliPai KernelSuAlignedBottomBar 架构）
 * liquidGlass = true → 液态玻璃效果
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
    playerBarWhiteBlend: Float = 0.8f
) {
    if (isFloating) {
        KernelSuAlignedBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            blurEnabled = blurEnabled,
            liquidGlass = liquidGlass,
            backdrop = backdrop,
            followThemeColor = followThemeColor,
            navBarOpacity = navBarOpacity,
            playerBarWhiteBlend = playerBarWhiteBlend
        )
    } else {
        DefaultBottomBar(
            selectedTabIndex = selectedTabIndex,
            onTabSelected = onTabSelected,
            blurEnabled = blurEnabled,
            navBarOpacity = navBarOpacity
        )
    }
}

// ==================== 默认导航栏（Neri 风格：全宽贴底 + 半透明玻璃感） ====================

@Composable
private fun DefaultBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    blurEnabled: Boolean,
    navBarOpacity: Float = 1f
) {
    // 纯色底栏：颜色完全等于主背景（随主题色/种子色变化），把导航栏视为背景的一部分
    val barColor = MaterialTheme.colorScheme.background.copy(alpha = navBarOpacity.coerceIn(0f, 1f))

    // 全宽贴底，无胶囊悬浮
    val barShape = RoundedCornerShape(0.dp)
    // 选中指示器：secondaryContainer 色块（对齐 Neri 的 DEFAULT_BOTTOM_BAR_SELECTION_ALPHA = 0.72）
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
