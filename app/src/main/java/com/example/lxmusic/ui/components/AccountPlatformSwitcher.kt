package com.example.lxmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.lxmusic.ui.components.topbar.ModernCapsuleContainer
import com.kyant.backdrop.Backdrop
import com.example.lxmusic.R
import kotlinx.coroutines.delay

/** 账号平台取值（与 mine_state/account_card_selected 存的字符串一致） */
const val ACCOUNT_PLATFORM_KUGOU = "kugou"
const val ACCOUNT_PLATFORM_NETEASE = "netease"

/** 平台品牌徽标：酷狗蓝底音符 K / 网易云官方云音符 */
@Composable
fun PlatformBrandIcon(platform: String, size: Dp, modifier: Modifier = Modifier) {
    val res = if (platform == ACCOUNT_PLATFORM_NETEASE) {
        R.drawable.ic_brand_netease
    } else {
        R.drawable.ic_brand_kugou
    }
    Image(
        painter = painterResource(res),
        contentDescription = if (platform == ACCOUNT_PLATFORM_NETEASE) "网易云音乐" else "酷狗音乐",
        modifier = modifier.size(size)
    )
}

/**
 * 「我的」顶栏的账号平台切换器。
 *
 * 收起时只显示当前平台的徽标，且**不画任何按键底/边框/玻璃**（液态玻璃开启时也不画），
 * 让它看起来就是压在顶栏上的一个品牌图标；点一下才长出胶囊底并向右展开另一家平台的徽标，
 * 点它即切换并收起。4 秒无操作自动收起，避免顶栏长期多出一截。
 *
 * 展开态材质与顶栏其它按键一致（[ModernCapsuleContainer]）：液态玻璃开启且传了 backdrop 时
 * 走同一套 vibrancy + 模糊 + lens 折射，否则用主题容器色 + 细边框。
 */
@Composable
fun AccountPlatformSwitcher(
    platform: String,
    onPlatformChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false
) {
    var expanded by remember(platform) { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(4000)
            expanded = false
        }
    }

    val other = if (platform == ACCOUNT_PLATFORM_NETEASE) {
        ACCOUNT_PLATFORM_KUGOU
    } else {
        ACCOUNT_PLATFORM_NETEASE
    }
    val dividerColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.22f)
    // 胶囊底随展开淡入淡出：收起时 alpha=0 → 背景层整个不组合（玻璃折射也一并省掉）
    val surfaceAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(180),
        label = "accountSwitcherSurface"
    )

    Box(modifier = modifier.height(34.dp)) {
        if (surfaceAlpha > 0.01f) {
            ModernCapsuleContainer(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = surfaceAlpha },
                backdrop = backdrop,
                liquidGlass = liquidGlass
            ) {}
        }

        Row(
            modifier = Modifier.fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 当前平台 + 「⇄」提示：点击展开/收起
            // 只有品牌图标时用户不知道这里能点，加个小切换箭头做可点性提示；
            // 展开后另一家平台的图标出现，箭头淡出避免挤占胶囊宽度。
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
                    .padding(start = 7.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PlatformBrandIcon(platform = platform, size = 20.dp)
                AnimatedVisibility(
                    visible = !expanded,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(90))
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "切换音乐平台",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandHorizontally(tween(180)) + fadeIn(tween(160)),
                exit = shrinkHorizontally(tween(150)) + fadeOut(tween(120))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(16.dp)
                            .background(dividerColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onPlatformChange(other)
                                expanded = false
                            }
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PlatformBrandIcon(platform = other, size = 20.dp)
                    }
                }
            }
        }
    }
}
