package com.example.lxmusic.ui.effect

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import org.intellij.lang.annotations.Language

/**
 * 液态玻璃背景效果Modifier
 * 基于BiliPai的实现，支持折射、色差和动态波纹效果
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Modifier.liquidGlassBackground(
    refractIntensity: Float = 0.4f,
    scrollOffsetProvider: () -> Float,
    backgroundColor: Color = Color.Black.copy(alpha = 0.8f),
    chromaticAberration: Float = 0.5f,
    blurRadius: Float = 50f
): Modifier = composed {
    val shader = remember { RuntimeShader(LiquidGlassShader.LIQUID_GLASS_SHADER) }
    
    // 创建高斯模糊效果
    val blurEffect = if (blurRadius > 0f) {
        RenderEffect.createBlurEffect(blurRadius, blurRadius, android.graphics.Shader.TileMode.CLAMP)
    } else {
        null
    }
    
    // 缓存RenderEffect避免每帧分配
    val liquidGlassRenderEffect = remember(shader, blurEffect) {
        val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "img")
        if (blurEffect != null) {
            // 将模糊效果与Shader效果组合
            RenderEffect.createChainEffect(blurEffect, shaderEffect)
                .asComposeRenderEffect()
        } else {
            shaderEffect.asComposeRenderEffect()
        }
    }

    this.graphicsLayer {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("refract_intensity", refractIntensity)
        shader.setFloatUniform("scroll_offset", scrollOffsetProvider())
        shader.setFloatUniform("chromatic_aberration", chromaticAberration)

        val bgColor = backgroundColor.toArgb()
        val a = android.graphics.Color.alpha(bgColor) / 255f
        val r = android.graphics.Color.red(bgColor) / 255f * a
        val g = android.graphics.Color.green(bgColor) / 255f * a
        val b = android.graphics.Color.blue(bgColor) / 255f * a
        shader.setFloatUniform("background_color", r, g, b, a)

        renderEffect = liquidGlassRenderEffect
    }
}

/**
 * 液态玻璃Shader
 * 实现折射、色差和动态波纹效果
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
object LiquidGlassShader {
    @Language("AGSL")
    const val LIQUID_GLASS_SHADER = """
        uniform shader img;
        uniform float2 resolution;
        uniform float refract_intensity;
        uniform float scroll_offset;
        uniform float chromatic_aberration;
        uniform float4 background_color;

        half4 main(in float2 fragCoord) {
            half2 uv = fragCoord;
            
            // 创建垂直波纹扭曲，基于滚动位置
            float scrollProgress = scroll_offset * 0.008;
            float waveX = sin(fragCoord.x * 0.025 + scrollProgress) * 0.6;
            float waveY = cos(fragCoord.y * 0.015 + scrollProgress * 0.5) * 0.8;
            float wave = waveX * waveY + sin(scrollProgress * 0.3) * 0.4;
            
            // 增强折射效果
            float2 offset = float2(wave * 0.4, wave * 1.2) * refract_intensity * 50.0;
            
            half2 minUV = half2(0.0);
            half2 maxUV = half2(resolution.x - 1.0, resolution.y - 1.0);
            uv = clamp(uv + offset, minUV, maxUV);
            
            // 增强色差效果
            float aberration = chromatic_aberration * refract_intensity;
            half r = img.eval(clamp(uv + float2(aberration * 4.0, aberration * 1.5), minUV, maxUV)).r;
            half g = img.eval(uv).g;
            half b = img.eval(clamp(uv - float2(aberration * 4.0, aberration * 1.5), minUV, maxUV)).b;
            half4 sampled = half4(r, g, b, 1.0);
            
            // 与半透明背景混合
            half4 result = sampled * (1.0 - background_color.a) + background_color;
            
            return result;
        }
    """
}