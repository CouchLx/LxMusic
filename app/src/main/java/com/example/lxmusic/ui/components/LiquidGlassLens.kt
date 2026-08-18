package com.example.lxmusic.ui.components

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtLeast
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.runtimeShaderEffect
import org.intellij.lang.annotations.Language

@Language("AGSL")
private const val SmoothRefractionShaderString = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

float sdRoundedRectVar(float2 coord, float2 halfSize, float4 radii) {
    float r = (coord.x >= 0.0) ? 
              ((coord.y >= 0.0) ? radii.z : radii.y) : 
              ((coord.y >= 0.0) ? radii.w : radii.x);
    float2 q = abs(coord) - halfSize + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 getSDFNormal(float2 coord, float2 halfSize, float4 radii) {
    float eps = 1.0;
    float dx = sdRoundedRectVar(coord + float2(eps, 0.0), halfSize, radii) - sdRoundedRectVar(coord - float2(eps, 0.0), halfSize, radii);
    float dy = sdRoundedRectVar(coord + float2(0.0, eps), halfSize, radii) - sdRoundedRectVar(coord - float2(0.0, eps), halfSize, radii);
    float2 n = float2(dx, dy);
    float len = length(n);
    return len > 0.0001 ? n / len : float2(0.0);
}

float circleMap(float x) {
    return 1.0 - sqrt(max(0.0, 1.0 - x * x));
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    
    float sd = sdRoundedRectVar(centeredCoord, halfSize, cornerRadii);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);
    
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float2 grad = getSDFNormal(centeredCoord, halfSize, cornerRadii);
    if (depthEffect > 0.0) {
        float2 depthVec = normalize(centeredCoord);
        grad = normalize(grad + depthEffect * depthVec);
    }
    
    float2 refractedCoord = coord + d * grad;
    
    if (chromaticAberration > 0.0) {
        float dispersionIntensity = chromaticAberration * 0.015;
        float2 dispersedCoord = d * grad * dispersionIntensity;
        
        half4 red = content.eval(refractedCoord + dispersedCoord);
        half4 green = content.eval(refractedCoord);
        half4 blue = content.eval(refractedCoord - dispersedCoord);
        
        return half4(red.r, green.g, blue.b, (red.a + green.a + blue.a) / 3.0);
    }
    
    return content.eval(refractedCoord);
}
"""

fun BackdropEffectScope.smoothLiquidLens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false
) {
    var p = padding
    if (p > 0f) {
        padding = (p - refractionHeight).fastCoerceAtLeast(0f)
    }
    val radii = extractCornerRadii(shape) ?: floatArrayOf(0f, 0f, 0f, 0f)
    runtimeShaderEffect(
        key = "SmoothLiquidLens_${depthEffect}_$chromaticAberration",
        shaderString = SmoothRefractionShaderString,
        uniformShaderName = "content"
    ) {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("offset", -p, -p)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("refractionHeight", refractionHeight)
        setFloatUniform("refractionAmount", -refractionAmount)
        setFloatUniform("depthEffect", if (depthEffect) 0.5f else 0.0f)
        setFloatUniform("chromaticAberration", if (chromaticAberration) 1.0f else 0.0f)
    }
}

private fun BackdropEffectScope.extractCornerRadii(shape: Shape): FloatArray? = when (shape) {
    is AbsoluteRoundedCornerShape -> {
        val maxRadius = size.minDimension / 2f
        val topLeft = shape.topStart.toPx(size, this)
        val topRight = shape.topEnd.toPx(size, this)
        val bottomRight = shape.bottomEnd.toPx(size, this)
        val bottomLeft = shape.bottomStart.toPx(size, this)
        floatArrayOf(
            topLeft.fastCoerceAtMost(maxRadius),
            topRight.fastCoerceAtMost(maxRadius),
            bottomRight.fastCoerceAtMost(maxRadius),
            bottomLeft.fastCoerceAtMost(maxRadius)
        )
    }
    is CornerBasedShape -> {
        val maxRadius = size.minDimension / 2f
        val isLtr = layoutDirection == LayoutDirection.Ltr
        val topLeft = if (isLtr) shape.topStart.toPx(size, this) else shape.topEnd.toPx(size, this)
        val topRight = if (isLtr) shape.topEnd.toPx(size, this) else shape.topStart.toPx(size, this)
        val bottomRight = if (isLtr) shape.bottomEnd.toPx(size, this) else shape.bottomStart.toPx(size, this)
        val bottomLeft = if (isLtr) shape.bottomStart.toPx(size, this) else shape.bottomEnd.toPx(size, this)
        floatArrayOf(
            topLeft.fastCoerceAtMost(maxRadius),
            topRight.fastCoerceAtMost(maxRadius),
            bottomRight.fastCoerceAtMost(maxRadius),
            bottomLeft.fastCoerceAtMost(maxRadius)
        )
    }
    else -> null
}
