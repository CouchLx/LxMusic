package com.example.lxmusic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 应用主题配置系统
 * 统一管理不同模式下的配色方案
 */
@Immutable
data class AppThemeConfig(
    // === 卡片/对话框配色 ===
    val cardBackground: Color,           // 卡片背景色
    val cardSurface: Color,              // 卡片表面色
    val dialogBackground: Color,         // 对话框背景色
    val dialogSurface: Color,            // 对话框表面色
    val bottomSheetBackground: Color,    // 底部表单背景色
    
    // === 导航栏配色 ===
    val navBarBackground: Color,         // 导航栏背景
    val navBarSurface: Color,            // 导航栏表面
    val navBarItemBackground: Color,     // 导航栏项背景
    val navBarItemActive: Color,         // 导航栏项选中色
    val navBarItemInactive: Color,       // 导航栏项未选中色
    
    // === 播放条配色 ===
    val playerBarBackground: Color,      // 播放条背景
    val playerBarSurface: Color,         // 播放条表面
    
    // === 列表项配色 ===
    val listItemBackground: Color,       // 列表项背景
    val listItemSurface: Color,          // 列表项表面
    val listItemHover: Color,            // 列表项悬停色
    
    // === 输入框配色 ===
    val inputBackground: Color,          // 输入框背景
    val inputSurface: Color,             // 输入框表面
    val inputBorder: Color,              // 输入框边框
    
    // === 按钮配色 ===
    val buttonPrimary: Color,            // 主按钮
    val buttonSecondary: Color,          // 次按钮
    val buttonSurface: Color,            // 按钮表面
    
    // === 分隔线和背景 ===
    val divider: Color,                  // 分隔线
    val background: Color,               // 页面背景
    val surface: Color,                  // 表面色
    val surfaceVariant: Color,           // 表面变体
    
    // === 文字配色 ===
    val textPrimary: Color,              // 主要文字
    val textSecondary: Color,            // 次要文字
    val textOnPrimary: Color,            // 主色上的文字
    val textOnSurface: Color,            // 表面上的文字
    
    // === 特殊效果 ===
    val overlay: Color,                  // 遮罩层
    val scrim: Color,                    // 暗淡层
    val shadow: Color                    // 阴影
)

/**
 * 主题配置提供者
 */
object ThemeConfigProvider {
    
    /**
     * 获取当前主题配置
     * @param isFloatingBottomBar 是否开启悬浮底栏
     * @param isDynamicColor 是否开启动态主题
     * @param customPrimaryColor 自定义主色（仅非动态主题时使用）
     */
    @Composable
    fun getConfig(
        isFloatingBottomBar: Boolean = false,
        isDynamicColor: Boolean = true,
        customPrimaryColor: Color? = null
    ): AppThemeConfig {
        val isDark = isSystemInDarkTheme()
        
        return if (isFloatingBottomBar) {
            // 悬浮底栏模式配置
            if (isDark) floatingBottomBarDarkConfig() else floatingBottomBarLightConfig()
        } else {
            // 默认模式配置
            if (isDark) defaultDarkConfig() else defaultLightConfig()
        }
    }
    
    /**
     * 默认浅色主题配置
     */
    private fun defaultLightConfig() = AppThemeConfig(
        // 卡片/对话框 - 使用不透明白色，确保可读性
        cardBackground = Color.White,
        cardSurface = Color.White,
        dialogBackground = Color.White,
        dialogSurface = Color.White,
        bottomSheetBackground = Color.White,
        
        // 导航栏
        navBarBackground = Color.White,
        navBarSurface = Color.White,
        navBarItemBackground = Color.Transparent,
        navBarItemActive = Color(0xFF6750A4),
        navBarItemInactive = Color(0xFF757575),
        
        // 播放条
        playerBarBackground = Color.White,
        playerBarSurface = Color.White,
        
        // 列表项
        listItemBackground = Color.White,
        listItemSurface = Color.White,
        listItemHover = Color(0xFFF5F5F5),
        
        // 输入框
        inputBackground = Color.White,
        inputSurface = Color.White,
        inputBorder = Color(0xFFE0E0E0),
        
        // 按钮
        buttonPrimary = Color(0xFF6750A4),
        buttonSecondary = Color(0xFFE8DEF8),
        buttonSurface = Color.White,
        
        // 分隔线和背景
        divider = Color(0xFFE0E0E0),
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        surfaceVariant = Color(0xFFF5F5F5),
        
        // 文字
        textPrimary = Color(0xFF1C1B1F),
        textSecondary = Color(0xFF757575),
        textOnPrimary = Color.White,
        textOnSurface = Color(0xFF1C1B1F),
        
        // 特殊效果
        overlay = Color.Black.copy(alpha = 0.32f),
        scrim = Color.Black.copy(alpha = 0.5f),
        shadow = Color.Black.copy(alpha = 0.15f)
    )
    
    /**
     * 默认深色主题配置
     */
    private fun defaultDarkConfig() = AppThemeConfig(
        // 卡片/对话框 - 使用深色不透明背景
        cardBackground = Color(0xFF1E1E1E),
        cardSurface = Color(0xFF2D2D2D),
        dialogBackground = Color(0xFF1E1E1E),
        dialogSurface = Color(0xFF2D2D2D),
        bottomSheetBackground = Color(0xFF1E1E1E),
        
        // 导航栏
        navBarBackground = Color(0xFF1E1E1E),
        navBarSurface = Color(0xFF2D2D2D),
        navBarItemBackground = Color.Transparent,
        navBarItemActive = Color(0xFFD0BCFF),
        navBarItemInactive = Color(0xFF9E9E9E),
        
        // 播放条
        playerBarBackground = Color(0xFF1E1E1E),
        playerBarSurface = Color(0xFF2D2D2D),
        
        // 列表项
        listItemBackground = Color(0xFF1E1E1E),
        listItemSurface = Color(0xFF2D2D2D),
        listItemHover = Color(0xFF3D3D3D),
        
        // 输入框
        inputBackground = Color(0xFF2D2D2D),
        inputSurface = Color(0xFF2D2D2D),
        inputBorder = Color(0xFF4D4D4D),
        
        // 按钮
        buttonPrimary = Color(0xFFD0BCFF),
        buttonSecondary = Color(0xFF4A4458),
        buttonSurface = Color(0xFF2D2D2D),
        
        // 分隔线和背景
        divider = Color(0xFF3D3D3D),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        surfaceVariant = Color(0xFF2D2D2D),
        
        // 文字
        textPrimary = Color(0xFFE6E1E5),
        textSecondary = Color(0xFF9E9E9E),
        textOnPrimary = Color(0xFF381E72),
        textOnSurface = Color(0xFFE6E1E5),
        
        // 特殊效果
        overlay = Color.Black.copy(alpha = 0.5f),
        scrim = Color.Black.copy(alpha = 0.7f),
        shadow = Color.Black.copy(alpha = 0.3f)
    )
    
    /**
     * 悬浮底栏浅色主题配置
     */
    private fun floatingBottomBarLightConfig() = defaultLightConfig().copy(
        // 悬浮底栏模式下，播放条使用白色背景
        playerBarBackground = Color.White,
        playerBarSurface = Color.White,
        
        // 导航栏使用液态玻璃效果背景
        navBarBackground = Color.White.copy(alpha = 0.8f),
        navBarSurface = Color.White.copy(alpha = 0.9f)
    )
    
    /**
     * 悬浮底栏深色主题配置
     */
    private fun floatingBottomBarDarkConfig() = defaultDarkConfig().copy(
        // 悬浮底栏模式下，播放条使用深色背景
        playerBarBackground = Color(0xFF2D2D2D),
        playerBarSurface = Color(0xFF3D3D3D),
        
        // 导航栏使用液态玻璃效果背景
        navBarBackground = Color(0xFF1E1E1E).copy(alpha = 0.8f),
        navBarSurface = Color(0xFF2D2D2D).copy(alpha = 0.9f)
    )
}

/**
 * 扩展函数：获取当前主题配置
 */
@Composable
fun getCurrentThemeConfig(): AppThemeConfig {
    return ThemeConfigProvider.getConfig()
}
