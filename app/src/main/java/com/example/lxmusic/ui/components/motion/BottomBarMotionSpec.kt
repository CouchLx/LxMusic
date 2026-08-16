package com.example.lxmusic.ui.components.motion

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

internal data class MotionSpringConfig(
    val dampingRatio: Float,
    val stiffness: Float,
    val visibilityThreshold: Float? = null
) {
    fun toSpringSpec(): SpringSpec<Float> {
        return spring(
            dampingRatio = dampingRatio,
            stiffness = stiffness,
            visibilityThreshold = visibilityThreshold
        )
    }
}

internal data class BottomBarDragMotionSpec(
    val baseResistance: Float,
    val overscrollResistance: Float,
    val overscrollLimitItems: Float,
    val flingProjectionTimeSeconds: Float,
    val maxReleaseStepCount: Int,
    val pressSpring: MotionSpringConfig,
    val selectionSpring: MotionSpringConfig,
    val offsetSnapSpring: MotionSpringConfig
)

internal data class BottomBarIndicatorMotionSpec(
    val deformationScaleXDelta: Float,
    val deformationScaleYCompressionRatio: Float,
    val scaleSpring: MotionSpringConfig,
    val dragScaleSpring: MotionSpringConfig,
    val capsuleVelocityNormalizationDivisor: Float,
    val capsuleVelocityScaleXMultiplier: Float,
    val capsuleVelocityScaleYMultiplier: Float,
    val capsuleVelocityClamp: Float
)

internal data class BottomBarMotionSpec(
    val drag: BottomBarDragMotionSpec,
    val indicator: BottomBarIndicatorMotionSpec
)

internal enum class BottomBarMotionProfile {
    DEFAULT,
    ANDROID_NATIVE_FLOATING
}

internal fun resolveBottomBarMotionSpec(
    profile: BottomBarMotionProfile = BottomBarMotionProfile.DEFAULT
): BottomBarMotionSpec {
    val base = createDefaultBottomBarMotionSpec()
    return when (profile) {
        BottomBarMotionProfile.DEFAULT -> base
        BottomBarMotionProfile.ANDROID_NATIVE_FLOATING -> base.copy(
            drag = base.drag.copy(
                baseResistance = 0.7f,
                overscrollResistance = 0.2f,
                flingProjectionTimeSeconds = 0.08f,
                selectionSpring = MotionSpringConfig(
                    dampingRatio = 0.9f,
                    stiffness = 200f
                ),
                offsetSnapSpring = MotionSpringConfig(
                    dampingRatio = 0.85f,
                    stiffness = 250f
                )
            ),
            indicator = base.indicator.copy(
                deformationScaleXDelta = 0.40f,
                deformationScaleYCompressionRatio = 0.54f,
                scaleSpring = MotionSpringConfig(
                    dampingRatio = 0.46f,
                    stiffness = 620f
                ),
                dragScaleSpring = MotionSpringConfig(
                    dampingRatio = 0.54f,
                    stiffness = 400f
                ),
                capsuleVelocityNormalizationDivisor = 10.5f,
                capsuleVelocityScaleXMultiplier = 0.72f,
                capsuleVelocityScaleYMultiplier = 0.24f,
                capsuleVelocityClamp = 0.20f
            )
        )
    }
}

private fun createDefaultBottomBarMotionSpec(): BottomBarMotionSpec {
    return BottomBarMotionSpec(
        drag = BottomBarDragMotionSpec(
            // 拖动跟手度：1.0=1:1 跟随，调到 1.3f 让手指动一点指示器就明显跟过来，手感更灵敏
            baseResistance = 1.3f,
            overscrollResistance = 0.3f,
            overscrollLimitItems = 0.5f,
            flingProjectionTimeSeconds = 0.2f,
            maxReleaseStepCount = 1,
            pressSpring = MotionSpringConfig(
                dampingRatio = 1f,
                stiffness = 1000f,
                visibilityThreshold = 0.001f
            ),
            selectionSpring = MotionSpringConfig(
                dampingRatio = 0.82f,
                stiffness = 500f
            ),
            offsetSnapSpring = MotionSpringConfig(
                dampingRatio = 0.78f,
                stiffness = 420f
            )
        ),
        indicator = BottomBarIndicatorMotionSpec(
            deformationScaleXDelta = 0.34f,
            deformationScaleYCompressionRatio = 0.52f,
            scaleSpring = MotionSpringConfig(
                dampingRatio = 0.5f,
                stiffness = 600f
            ),
            dragScaleSpring = MotionSpringConfig(
                dampingRatio = 0.6f,
                stiffness = 400f
            ),
            capsuleVelocityNormalizationDivisor = 10f,
            capsuleVelocityScaleXMultiplier = 0.75f,
            capsuleVelocityScaleYMultiplier = 0.25f,
            capsuleVelocityClamp = 0.2f
        )
    )
}
