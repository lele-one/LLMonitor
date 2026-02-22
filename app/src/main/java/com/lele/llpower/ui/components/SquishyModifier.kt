package com.lele.llpower.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A Material 3 Expressive "Squishy" modifier that scales down on press.
 */
fun Modifier.squishyClickable(
    interactionSource: MutableInteractionSource? = null,
    indication: Indication? = null, // Set to null to use LocalIndication (Ripple)
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SquishyScale"
    )

    this
        .graphicsLayer(scaleX = scale, scaleY = scale)
        .clickable(
            interactionSource = actualSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
