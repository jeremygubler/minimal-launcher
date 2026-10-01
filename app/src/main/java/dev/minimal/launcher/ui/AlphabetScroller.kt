package dev.minimal.launcher.ui

import dev.minimal.launcher.util.tr
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

/**
 * Buchstabenleiste am Bildschirmrand. Beim Ziehen wölben sich die Buchstaben um den
 * Finger herum wie eine Welle nach innen – ähnlich wie bei Niagara.
 */
@Composable
fun AlphabetScroller(
    letters: List<String>,
    onLeft: Boolean,
    onLetter: (String) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    var heightPx by remember { mutableIntStateOf(1) }
    var touchY by remember { mutableFloatStateOf(-1f) }
    var dragging by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(-1) }
    val currentLetters by rememberUpdatedState(letters)
    val currentOnLetter by rememberUpdatedState(onLetter)
    val currentOnDrag by rememberUpdatedState(onDragStateChanged)

    // Überhöhter Bereich für die Welle, damit Buchstaben nicht abgeschnitten werden.
    val waveWidth = 96.dp
    val barWidth = 36.dp

    Box(
        modifier
            .width(waveWidth)
            .fillMaxHeight(),
        contentAlignment = if (onLeft) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Column(
            Modifier
                .width(barWidth)
                .fillMaxHeight()
                .onSizeChanged { heightPx = max(1, it.height) }
                // Für TalkBack: ein Knopf „Alle Apps“ statt 27 einzelner Buchstaben.
                .clearAndSetSemantics {
                    contentDescription = tr("Buchstabenleiste – alle Apps", "Letter bar – all apps")
                    role = Role.Button
                    onClick(label = tr("Alle Apps öffnen", "Open all apps")) {
                        currentLetters.firstOrNull()?.let(currentOnLetter)
                        true
                    }
                }
                .pointerInput(Unit) {
                    fun update(y: Float) {
                        touchY = y
                        val list = currentLetters
                        val idx = ((y / heightPx) * list.size).toInt().coerceIn(0, list.lastIndex)
                        if (idx != activeIndex) {
                            activeIndex = idx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnLetter(list[idx])
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        dragging = true
                        currentOnDrag(true)
                        activeIndex = -1
                        update(down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            update(change.position.y)
                            change.consume()
                        }
                        dragging = false
                        touchY = -1f
                        currentOnDrag(false)
                    }
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rowHeightPx = heightPx.toFloat() / letters.size
            letters.forEachIndexed { i, letter ->
                val centerY = rowHeightPx * (i + 0.5f)
                val distance = if (touchY < 0) Float.MAX_VALUE else abs(centerY - touchY) / rowHeightPx
                val strength = if (dragging) max(0f, 1f - distance / 4f) else 0f
                val animated by animateFloatAsState(strength, label = "wave")
                val shiftPx = with(density) { 56.dp.toPx() } * animated * (if (onLeft) 1 else -1)
                val active = dragging && i == activeIndex
                Text(
                    letter,
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) MaterialTheme.colorScheme.primary else LocalHomeColors.current.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationX = shiftPx
                            val scale = 1f + animated * 1.1f
                            scaleX = scale
                            scaleY = scale
                        },
                )
            }
        }
    }
}
