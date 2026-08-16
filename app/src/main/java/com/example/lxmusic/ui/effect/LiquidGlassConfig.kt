package com.example.lxmusic.ui.effect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 液态玻璃配置
 */
data class LiquidGlassConfig(
    val enabled: Boolean = false,
    val refractIntensity: Float = 0.4f,
    val chromaticAberration: Float = 0.5f,
    val backgroundColor: Color = Color.Black.copy(alpha = 0.8f),
    val blurRadius: Float = 50f,
    val cornerRadius: Float = 32f,
    val animationEnabled: Boolean = true
)

/**
 * 液态玻璃预设
 */
enum class LiquidGlassPreset {
    // 清晰模式 - 最少效果
    CLEAR {
        override fun getConfig(): LiquidGlassConfig = LiquidGlassConfig(
            enabled = true,
            refractIntensity = 0.1f,
            chromaticAberration = 0.2f,
            blurRadius = 10f,
            animationEnabled = false
        )
    },
    
    // 平衡模式 - 强化磨砂和模糊效果，提高可读性
    BALANCED {
        override fun getConfig(): LiquidGlassConfig = LiquidGlassConfig(
            enabled = true,
            refractIntensity = 0.4f,
            chromaticAberration = 0.5f,
            blurRadius = 50f,
            backgroundColor = Color.Black.copy(alpha = 0.8f),
            animationEnabled = true
        )
    },
    
    // 霜冻模式 - 最强效果
    FROSTED {
        override fun getConfig(): LiquidGlassConfig = LiquidGlassConfig(
            enabled = true,
            refractIntensity = 0.4f,
            chromaticAberration = 0.6f,
            blurRadius = 40f,
            backgroundColor = Color.Black.copy(alpha = 0.8f),
            animationEnabled = true
        )
    },
    
    // 禁用模式
    DISABLED {
        override fun getConfig(): LiquidGlassConfig = LiquidGlassConfig(
            enabled = false
        )
    };
    
    abstract fun getConfig(): LiquidGlassConfig
}

/**
 * 液态玻璃强度枚举
 */
enum class LiquidGlassIntensity {
    THIN,    // 薄雾效果
    MEDIUM,  // 中等效果
    THICK,   // 浓雾效果
    NONE     // 无效果
}

/**
 * 根据强度获取配置
 */
fun LiquidGlassIntensity.toConfig(): LiquidGlassConfig = when (this) {
    LiquidGlassIntensity.THIN -> LiquidGlassConfig(
        enabled = true,
        refractIntensity = 0.3f,
        chromaticAberration = 0.4f,
        blurRadius = 40f,
        backgroundColor = Color.Black.copy(alpha = 0.6f)
    )
    LiquidGlassIntensity.MEDIUM -> LiquidGlassConfig(
        enabled = true,
        refractIntensity = 0.4f,
        chromaticAberration = 0.5f,
        blurRadius = 50f,
        backgroundColor = Color.Black.copy(alpha = 0.8f)
    )
    LiquidGlassIntensity.THICK -> LiquidGlassConfig(
        enabled = true,
        refractIntensity = 0.5f,
        chromaticAberration = 0.6f,
        blurRadius = 60f,
        backgroundColor = Color.Black.copy(alpha = 0.9f)
    )
    LiquidGlassIntensity.NONE -> LiquidGlassConfig(
        enabled = false
    )
}