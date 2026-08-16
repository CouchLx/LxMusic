package com.example.lxmusic.ui.components.bottombar

import com.example.lxmusic.R
import com.example.lxmusic.ui.components.HapticFeedbackEffect
import com.example.lxmusic.ui.components.performHapticFeedback
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import kotlin.math.sign
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lxmusic.ui.components.animation.DampedDragAnimationState
import com.example.lxmusic.ui.components.animation.KERNEL_SU_PRESSED_SCALE
import com.example.lxmusic.ui.components.animation.horizontalDragGesture
import com.example.lxmusic.ui.components.animation.rememberDampedDragAnimationState
import com.example.lxmusic.ui.components.motion.BottomBarMotionProfile
import com.example.lxmusic.ui.components.motion.resolveBottomBarMotionSpec
import com.example.lxmusic.ui.theme.LocalScaleFactor
import com.example.lxmusic.ui.theme.ScreenAdapter
import com.kyant.backdrop.Backdrop

/**
 * 胶囊底栏主组件（对齐 BiliPai KernelSuAlignedBottomBar）
 *
 * 4层架构：
 * Layer 0 - Shell：外壳容器（液态玻璃 or 纯色）
 * Layer 1 - Tab 图标层：静态图标和文字
 * Layer 2 - 指示器层：移动的胶囊高亮块（液态玻璃 or 纯色 + 果冻形变）
 * Layer 3 - 输入层：点击 + 拖动手势
 */
@Composable
fun KernelSuAlignedBottomBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    blurEnabled: Boolean,
    liquidGlass: Boolean,
    backdrop: Backdrop?,
    followThemeColor: Boolean = false,
    navBarOpacity: Float = 1f,
    // 播放条白色混合系数：指示器颜色与播放条保持一致
    playerBarWhiteBlend: Float = 0.8f
) {
    val isDarkTheme = false // 可以从主题获取
    val tuning = resolveAndroidNativeBottomBarTuning()
    val shellShape = resolveSharedBottomBarCapsuleShape()
    val motionSpec = remember {
        resolveBottomBarMotionSpec(BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)
    }

    // 玻璃材质参数
    val glassMaterialSpec = resolveBottomBarGlassMaterialSpec(
        preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        isDarkTheme = isDarkTheme,
        isScrolling = false,
        glassEnabled = liquidGlass,
        motionProgress = 0f,
        pressProgress = 0f
    )

    // Shell 容器色：现代化底栏背景固定白色（不随主题/动态取色变化），
    // 播放条跟随主题色由 MiniPlayerBar 侧处理；原生主题底栏不受影响
    val baseContainerColor = resolveKernelSuBottomBarShellColor(
        containerColor = Color.White,
        liquidGlassEnabled = liquidGlass,
        darkTheme = isDarkTheme
    )
    // 当不透明度为100%时，使用完全不透明的颜色
    val containerColor = if (navBarOpacity >= 1f && !liquidGlass) {
        baseContainerColor.copy(alpha = 1f)
    } else {
        baseContainerColor
    }

    // 指示器效果开关
    val indicatorEffectsEnabled = resolveBottomBarIndicatorEffectsEnabled(
        liquidGlassEnabled = liquidGlass,
        blurEnabled = blurEnabled
    )

    // 指示器 lens 参数
    val indicatorLensSpec = resolveBottomBarBackdropPresetIndicatorLens(
        preset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        glassEnabled = liquidGlass
    )

    // Tab 图标颜色（支持随主题颜色变化 + 不透明度控制颜色深浅）
    val tabIconColor = if (followThemeColor) {
        // 使用主题主色，navBarOpacity控制饱和度/对比度
        // 100% = 纯主题色，0% = 接近白色
        val themeColor = MaterialTheme.colorScheme.primary
        val blendFactor = (1f - navBarOpacity) * 0.8f // 0%时blend=0.8(接近白色)，100%时blend=0(纯色)
        Color(
            red = themeColor.red + (1f - themeColor.red) * blendFactor,
            green = themeColor.green + (1f - themeColor.green) * blendFactor,
            blue = themeColor.blue + (1f - themeColor.blue) * blendFactor,
            alpha = 1f
        )
    } else {
        Color(0xFF1A1A1A)
    }
    val indicatorIdleSurfaceColor = if (followThemeColor) {
        // 与播放条同款颜色（secondaryContainer + 播放条白色混合系数），
        // 半透明但足够明显（0.4~0.65），不遮挡 tab 图标
        val playerBarBase = MaterialTheme.colorScheme.secondaryContainer
        val alphaValue = 0.4f + navBarOpacity * 0.25f
        Color(
            red = playerBarBase.red + (1f - playerBarBase.red) * playerBarWhiteBlend,
            green = playerBarBase.green + (1f - playerBarBase.green) * playerBarWhiteBlend,
            blue = playerBarBase.blue + (1f - playerBarBase.blue) * playerBarWhiteBlend,
            alpha = alphaValue
        )
    } else {
        resolveAndroidNativeIdleIndicatorSurfaceColor(darkTheme = isDarkTheme)
    }
    // 选中项与未选中同色（跟随主题变化），区分靠指示器色块与文字加粗
    val selectedTabColor = tabIconColor

    // 指示器只采样主内容背景（backdrop），不再采样 tab 图标层。
    // 这样指示器的 blur 只模糊「下方页面内容」，而 tab 图标绘制在指示器之上保持清晰。
    val indicatorBackdrop: Backdrop? = if (liquidGlass && backdrop != null) {
        backdrop
    } else {
        null
    }

    val itemCount = 3
    val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.Person)
    val labels = listOf(
        androidx.compose.ui.res.stringResource(R.string.nav_home),
        androidx.compose.ui.res.stringResource(R.string.nav_discover),
        androidx.compose.ui.res.stringResource(R.string.nav_mine)
    )

    // 从 SharedPreferences 读取「点击切换动画速率」并同步到动画状态，
    // 设置页滑块修改该值后这里会实时生效。
    val context = androidx.compose.ui.platform.LocalContext.current
    val dampedDragState = rememberDampedDragAnimationState(
        initialIndex = selectedTabIndex,
        itemCount = itemCount,
        onIndexChanged = { index ->
            context.performHapticFeedback(HapticFeedbackEffect.Click)
            onTabSelected(index)
        },
        motionSpec = motionSpec,
        notifyIndexChangedOnReleaseStart = false
    )

    LaunchedEffect(dampedDragState) {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        // 启动时先同步一次
        dampedDragState.setClickStiffness(prefs.getFloat("click_animation_speed", 700f))
        // 注册 listener 监听设置页滑块的写入
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == "click_animation_speed") {
                dampedDragState.setClickStiffness(sp.getFloat("click_animation_speed", 700f))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    LaunchedEffect(selectedTabIndex) {
        dampedDragState.updateIndex(selectedTabIndex)
    }

    // 脉冲动效 keys
    var settleReboundPulseKey by remember { mutableIntStateOf(0) }
    var clickPulseKey by remember { mutableIntStateOf(0) }

    // 监听拖拽结束 → 触发落位回弹
    LaunchedEffect(dampedDragState.settledReleaseCount) {
        if (dampedDragState.settledReleaseCount > 0) settleReboundPulseKey++
    }
    // 监听选中变化 → 触发点击脉冲
    LaunchedEffect(dampedDragState.settledSelectionCount) {
        if (dampedDragState.settledSelectionCount > 0) clickPulseKey++
    }

    val settleRebound = rememberBottomBarSettleReboundTransform(settleReboundPulseKey)
    val clickPulse = rememberBottomBarClickPulseTransform(clickPulseKey)

    val density = LocalDensity.current

    // 获取屏幕缩放比例 - 内容缩放 vs 间距缩放
    val contentScaleFactor = LocalScaleFactor.current  // 用于图标、文字等
    val spacingScaleFactor = ScreenAdapter.getSpacingScaleFactor()  // 用于间距、高度

    // 布局全宽自适应（与原生主题一致，任意 UI 缩放/手机尺寸不溢出）：
    // 宽度计算与播放条完全相同：fillMaxWidth（父可用宽）− 左右各 2% 屏宽边距，
    // 任何容器/屏幕尺寸下两者严格同宽；item 区域 = dock/3 随容器自适应；
    // 高度随 UI 缩放（与播放条高度同步）
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val sidePaddingDp = screenWidthDp.dp * 0.02f
    val shellH = 64.dp * contentScaleFactor  // 高度随 UI 缩放
    val indH = 56.dp * contentScaleFactor  // 高度随 UI 缩放

    val density2 = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = sidePaddingDp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 与播放条同语义：父可用宽度 − 2%×2
        val shellW = maxWidth
        val dockW = maxWidth
        val itemW = maxWidth / itemCount
        // ===== Layer 0: Shell =====
        KernelSuBottomBarShell(
            width = shellW,
            height = shellH,
            shellShape = shellShape,
            backdrop = if (liquidGlass) backdrop else null,
            containerColor = containerColor,
            glassEnabled = liquidGlass,
            glassMaterialSpec = glassMaterialSpec,
            pressProgress = dampedDragState.pressProgress
        )

        val vp = dampedDragState.value

        // ===== Layer 1: Tab 图标（仅非玻璃模式在此绘制）=====
        // 玻璃模式下，图标改为在指示器之上绘制（见 Layer 1b），保持清晰不被指示器模糊。
        // ===== Layer 2: 指示器 =====
        val pressP = dampedDragState.pressProgress
        // 形变：
        // - 玻璃模式：接入官方 DampedDragAnimation 果冻算法
        //   （press 缩放 + velocity 拖动方向拉伸 / 垂直挤压），完全对齐官方演示手感。
        // - 非玻璃模式：保持原有落位回弹 * 点击脉冲，逻辑不变，互不影响。
        val indScaleX: Float
        val indScaleY: Float
        if (liquidGlass && backdrop != null) {
            val jellyVelocity = dampedDragState.velocity / 10f
            var sx = dampedDragState.scaleX
            var sy = dampedDragState.scaleY
            sx /= 1f - (jellyVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
            sy *= 1f - (jellyVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
            indScaleX = sx
            indScaleY = sy
        } else {
            indScaleX = settleRebound.scaleX * clickPulse.scaleX
            indScaleY = settleRebound.scaleY * clickPulse.scaleY
        }
        // Row+SpaceEvenly 中 item i 的中心 = -dockW/2 + dockW*(2*i+1)/6
        // 指示器中心 = item 中心，所以 offset = 该值（offset 定位的是左边缘，但 Alignment.Center 已经居中）
        val indOffset = -dockW / 2 + dockW * (2 * vp + 1) / 6
        val indVerticalPad = (indH - shellH) / 2
        // 指示器胶囊：宽度 = item 宽 × 94% × UI 缩放（封顶 item 宽，不溢出），
        // 最左/最右时贴近 dock 边缘（距离仅 item 宽的 3%，任意 UI 尺寸等比例）；
        // 高度随 UI 缩放
        val indicatorWidth = (itemW * 0.94f * contentScaleFactor).coerceAtMost(itemW)
        KernelSuBottomBarIndicatorLayer(
            visible = true,
            indicatorWidth = indicatorWidth,
            indicatorHeight = indH,
            shellShape = shellShape,
            backdrop = indicatorBackdrop,
            indicatorLensSpec = indicatorLensSpec,
            effectivePressProgress = dampedDragState.pressProgress,
            indicatorIdleSurfaceColor = indicatorIdleSurfaceColor,
            glassEnabled = liquidGlass,
            indicatorEffectsEnabled = indicatorEffectsEnabled,
            indicatorScaleX = indScaleX,
            indicatorScaleY = indScaleY,
            modifier = Modifier.offset(x = indOffset, y = indVerticalPad)
        )

        // ===== Layer 2b: Tab 图标（所有模式统一绘制在指示器之上，保持清晰）=====
        // 图标不进入 backdrop 采样层，指示器色块/模糊只作用于下方内容，
        // 选中 tab 的图标始终清晰可见，不被指示器颜色影响
        Box(
            modifier = Modifier.width(dockW).height(shellH).align(Alignment.Center)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(shellH),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until itemCount) {
                    val isCur = i == selectedTabIndex
                    val iconSize = (22.dp * contentScaleFactor).coerceAtMost(24.dp)
                    val fontSize = kotlin.math.min(10f * contentScaleFactor, 11f).sp
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.weight(1f).height(shellH)
                    ) {
                        Icon(
                            icons[i], labels[i], Modifier.size(iconSize),
                            tint = if (isCur) selectedTabColor else tabIconColor
                        )
                        Text(labels[i], fontSize = fontSize,
                            fontWeight = if (isCur) FontWeight.Medium else FontWeight.Normal,
                            color = if (isCur) selectedTabColor else tabIconColor)
                    }
                }
            }
        }

        // ===== Layer 3: 输入层 =====
        val itemWpx = with(density2) { itemW.toPx() }
        Box(
            Modifier
                .width(dockW)
                .height(shellH)
                .horizontalDragGesture(
                    dragState = dampedDragState,
                    itemWidthPx = itemWpx,
                    consumePointerChanges = true,
                    notifyIndexChanged = true,
                    onTap = { index -> onTabSelected(index.coerceIn(0, itemCount - 1)) }
                )
        )
    }
}
