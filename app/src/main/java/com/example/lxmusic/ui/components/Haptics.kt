package com.example.lxmusic.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * 全局触感反馈（对齐 NeriPlayer 的 Haptic）。
 *
 * 通过 Android Vibrator 系统服务触发，效果比 Compose 的 HapticFeedback 更强更可靠，
 * 且开关通过全局变量即时生效（Composition 与任意线程均可调用）。
 */

private const val VIBRATION_EFFECT_CLICK = 0
private const val VIBRATION_EFFECT_DOUBLE_CLICK = 1
private const val VIBRATION_EFFECT_TICK = 2
private const val VIBRATION_EFFECT_HEAVY_CLICK = 5

enum class HapticFeedbackEffect(
    val predefinedEffect: Int,
    val fallbackDurationMs: Long,
    val fallbackAmplitude: Int
) {
    Tick(VIBRATION_EFFECT_TICK, 8L, 32),
    Click(VIBRATION_EFFECT_CLICK, 20L, 120),
    Confirm(VIBRATION_EFFECT_DOUBLE_CLICK, 30L, 180),
    Heavy(VIBRATION_EFFECT_HEAVY_CLICK, 40L, 255)
}

/** 全局开关：即时生效，任意代码位置可调用。 */
@Volatile
var hapticFeedbackEnabled: Boolean = true
    private set

fun syncHapticFeedbackSetting(enabled: Boolean) {
    hapticFeedbackEnabled = enabled
}

fun Context.performHapticFeedback(
    effect: HapticFeedbackEffect = HapticFeedbackEffect.Click
) {
    if (!hapticFeedbackEnabled) return
    val vibrator = getSystemService(Vibrator::class.java) ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        vibrator.vibrate(createVibrationEffect(vibrator, effect))
    }
}

private fun createVibrationEffect(
    vibrator: Vibrator,
    effect: HapticFeedbackEffect
): VibrationEffect {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        vibrator.supportsPredefinedEffect(effect.predefinedEffect)
    ) {
        return VibrationEffect.createPredefined(effect.predefinedEffect)
    }
    return VibrationEffect.createOneShot(effect.fallbackDurationMs, effect.fallbackAmplitude)
}

private fun Vibrator.supportsPredefinedEffect(effect: Int): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
    val support = areEffectsSupported(effect).firstOrNull()
    return support == Vibrator.VIBRATION_EFFECT_SUPPORT_YES ||
        support == Vibrator.VIBRATION_EFFECT_SUPPORT_UNKNOWN
}

/**
 * 触感反馈是否开启（Compose 侧读取）。
 */
@Composable
fun isHapticsEnabled(): Boolean = hapticFeedbackEnabled

/**
 * 在触感反馈开启时执行一次触感反馈（Compose 场景）。
 */
@Composable
fun performHapticFeedbackIfEnabled(
    effect: HapticFeedbackEffect = HapticFeedbackEffect.Click
) {
    if (hapticFeedbackEnabled) {
        LocalContext.current.performHapticFeedback(effect)
    }
}

@Composable
fun HapticsProvider(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    // 同步全局开关（立即生效），同时保留 CompositionLocal 供一次性读取
    syncHapticFeedbackSetting(enabled)
    CompositionLocalProvider(
        LocalHapticsEnabled provides enabled,
        content = content
    )
}

val LocalHapticsEnabled = staticCompositionLocalOf { true }
