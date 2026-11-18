package com.example.lxmusic.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.lxmusic.R

/**
 * 底栏图标（按「语义」命名，不按图形命名）。
 *
 * 想换一整套图标，只改这个文件即可，[com.example.lxmusic.MainActivity] 等调用点一行都不用动。
 *
 * 当前图标源：Material Symbols Rounded（Apache-2.0）
 * 由官方 SVG（960 视口、-960 原点）转成 VectorDrawable（res/drawable/ic_nav_*.xml）：
 *  - 选中态 = 实心（*-fill）
 *  - 未选中 = 线性
 *
 * 换图标源的常见流派（都只需替换本文件的 drawable 引用）：
 *  - 谷歌圆角：Material Symbols Rounded（当前）
 *  - 苹果风：Ionicons filled/outline 成对
 *  - 极简线性：Lucide / Phosphor（无实心版，选中靠加粗描边区分）
 */
object NavIcons {
    /** 首页（实心房子） */
    val HomeFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_home_filled)
    val HomeOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_home_outline)

    /** 发现（实心圆盘指南针） */
    val DiscoverFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_explore_filled)
    val DiscoverOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_explore_outline)

    /** 搜索（放大镜） */
    val SearchFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_search_filled)
    val SearchOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_search_outline)

    /** 我的（人像：选中实心 / 未选中空心） */
    val MineFilled: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_mine_filled)
    val MineOutline: ImageVector @Composable get() = ImageVector.vectorResource(R.drawable.ic_nav_mine_outline)
}
