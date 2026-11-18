package com.example.lxmusic.ui.roaming

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lxmusic.data.roaming.PersonalRoamingController

/**
 * 播放器里的私人漫游整页（仅网易云状态出现）。
 *
 * 不铺自己的底色：直接吃播放器那层跟随封面变化的动效背景，
 * 文字用播放器算出的前景色（uiTint / uiTintVariant），和歌词页同一个观感。
 * 进入这一页时歌名顶栏和底部进度/控制会收起，所以这里占满整屏。
 */
@Composable
fun PlayerRoamingPage(
    controller: PersonalRoamingController,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onBackToPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    tintVariant: Color = Color.White.copy(alpha = .55f)
) {
    // 用 LazyColumn 而不是 Column+verticalScroll：它和 HorizontalPager 的嵌套滚动配合正常，
    // 快速横滑不会被竖向滚动容器抢走（否则 pager 翻不回去）
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        // 顶部再留一点：标题不能贴着状态栏（用户觉得太靠上）
        contentPadding = PaddingValues(start = 20.dp, end = 26.dp, top = 30.dp, bottom = 20.dp)
    ) {
        item {
            PersonalRoamingContent(
                controller = controller,
                isLoggedIn = isLoggedIn,
                onLogin = onLogin,
                onContinue = onBackToPlayer,
                embedded = true,
                onDark = true,
                tint = tint,
                tintVariant = tintVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
