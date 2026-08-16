package com.example.lxmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor
import androidx.core.content.edit
import com.example.lxmusic.ui.effect.LiquidGlassConfig
import com.example.lxmusic.ui.effect.LiquidGlassIntensity
import com.example.lxmusic.ui.effect.LiquidGlassPreset

/**
 * 液态玻璃设置管理器
 */
class LiquidGlassSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("liquid_glass_settings", Context.MODE_PRIVATE)
    
    // 设置键
    companion object {
        private const val KEY_ENABLED = "liquid_glass_enabled"
        private const val KEY_INTENSITY = "liquid_glass_intensity"
        private const val KEY_REFRACT_INTENSITY = "refract_intensity"
        private const val KEY_CHROMATIC_ABERRATION = "chromatic_aberration"
        private const val KEY_BLUR_RADIUS = "blur_radius"
        private const val KEY_CORNER_RADIUS = "corner_radius"
        private const val KEY_ANIMATION_ENABLED = "animation_enabled"
        private const val KEY_BACKGROUND_COLOR = "background_color"
        private const val KEY_PRESET = "preset"
    }
    
    // 当前设置状态
    var _enabled: Boolean = getEnabled()
    var _intensity: LiquidGlassIntensity = getIntensity()
    var _refractIntensity: Float = getRefractIntensity()
    var _chromaticAberration: Float = getChromaticAberration()
    var _blurRadius: Float = getBlurRadius()
    var _cornerRadius: Float = getCornerRadius()
    var _animationEnabled: Boolean = getAnimationEnabled()
    var _backgroundColor: Color = getBackgroundColor()
    var _preset: LiquidGlassPreset = getPreset()
    
    // 获取设置值
    fun getEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun getIntensity(): LiquidGlassIntensity = 
        LiquidGlassIntensity.values().find { it.name == prefs.getString(KEY_INTENSITY, LiquidGlassIntensity.MEDIUM.name) }
            ?: LiquidGlassIntensity.MEDIUM
    fun getRefractIntensity(): Float = prefs.getFloat(KEY_REFRACT_INTENSITY, 0.4f)
    fun getChromaticAberration(): Float = prefs.getFloat(KEY_CHROMATIC_ABERRATION, 0.5f)
    fun getBlurRadius(): Float = prefs.getFloat(KEY_BLUR_RADIUS, 50f)
    fun getCornerRadius(): Float = prefs.getFloat(KEY_CORNER_RADIUS, 32f)
    fun getAnimationEnabled(): Boolean = prefs.getBoolean(KEY_ANIMATION_ENABLED, true)
    fun getBackgroundColor(): Color = 
        Color(
            prefs.getInt(KEY_BACKGROUND_COLOR, AndroidColor.argb(204, 0, 0, 0))  // 80% alpha black
        )
    fun getPreset(): LiquidGlassPreset = 
        LiquidGlassPreset.values().find { it.name == prefs.getString(KEY_PRESET, LiquidGlassPreset.BALANCED.name) }
            ?: LiquidGlassPreset.BALANCED
    
    // 保存设置值
    fun saveEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        this._enabled = enabled
    }
    
    fun saveIntensity(intensity: LiquidGlassIntensity) {
        prefs.edit { putString(KEY_INTENSITY, intensity.name) }
        this._intensity = intensity
    }
    
    fun saveRefractIntensity(refractIntensity: Float) {
        prefs.edit { putFloat(KEY_REFRACT_INTENSITY, refractIntensity) }
        this._refractIntensity = refractIntensity
    }
    
    fun saveChromaticAberration(chromaticAberration: Float) {
        prefs.edit { putFloat(KEY_CHROMATIC_ABERRATION, chromaticAberration) }
        this._chromaticAberration = chromaticAberration
    }
    
    fun saveBlurRadius(blurRadius: Float) {
        prefs.edit { putFloat(KEY_BLUR_RADIUS, blurRadius) }
        this._blurRadius = blurRadius
    }
    
    fun saveCornerRadius(cornerRadius: Float) {
        prefs.edit { putFloat(KEY_CORNER_RADIUS, cornerRadius) }
        this._cornerRadius = cornerRadius
    }
    
    fun saveAnimationEnabled(animationEnabled: Boolean) {
        prefs.edit { putBoolean(KEY_ANIMATION_ENABLED, animationEnabled) }
        this._animationEnabled = animationEnabled
    }
    
    fun saveBackgroundColor(backgroundColor: Color) {
        prefs.edit { putInt(KEY_BACKGROUND_COLOR, backgroundColor.toArgb()) }
        this._backgroundColor = backgroundColor
    }
    
    fun savePreset(preset: LiquidGlassPreset) {
        prefs.edit { putString(KEY_PRESET, preset.name) }
        this._preset = preset
    }
    
    // 应用预设
    fun applyPreset(preset: LiquidGlassPreset) {
        val config = preset.getConfig()
        saveEnabled(config.enabled)
        saveRefractIntensity(config.refractIntensity)
        saveChromaticAberration(config.chromaticAberration)
        saveBlurRadius(config.blurRadius)
        saveCornerRadius(config.cornerRadius)
        saveAnimationEnabled(config.animationEnabled)
        saveBackgroundColor(config.backgroundColor)
    }
    
    // 获取当前配置
    fun getCurrentConfig(): LiquidGlassConfig {
        return LiquidGlassConfig(
            enabled = _enabled,
            refractIntensity = _refractIntensity,
            chromaticAberration = _chromaticAberration,
            backgroundColor = _backgroundColor,
            blurRadius = _blurRadius,
            cornerRadius = _cornerRadius,
            animationEnabled = _animationEnabled
        )
    }
    
    // 重置为默认值
    fun resetToDefaults() {
        applyPreset(LiquidGlassPreset.BALANCED)
    }
}