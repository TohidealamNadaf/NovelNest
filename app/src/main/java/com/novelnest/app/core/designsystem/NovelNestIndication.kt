package com.novelnest.app.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Custom soft indication for NovelNest that provides a calm, elegant overlay
 * on press, hover, and focus without harsh or distracting visual bursts.
 * Respects component shapes via parent clipping and draw bounds.
 */
object NovelNestIndication : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode {
        return NovelNestIndicationNode(interactionSource)
    }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = -999
}

private class NovelNestIndicationNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {

    private val alpha = Animatable(0f)

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collectLatest { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        alpha.animateTo(0.12f, tween(100))
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        alpha.animateTo(0f, tween(250))
                    }
                    is FocusInteraction.Focus -> {
                        alpha.animateTo(0.08f, tween(150))
                    }
                    is FocusInteraction.Unfocus -> {
                        alpha.animateTo(0f, tween(200))
                    }
                    is HoverInteraction.Enter -> {
                        alpha.animateTo(0.05f, tween(150))
                    }
                    is HoverInteraction.Exit -> {
                        alpha.animateTo(0f, tween(200))
                    }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (alpha.value > 0f) {
            drawRect(color = Color.White.copy(alpha = alpha.value))
        }
    }
}
