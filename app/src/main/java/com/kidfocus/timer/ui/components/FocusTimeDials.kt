package com.kidfocus.timer.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.FocusTimePolicy
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.model.TimerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

private fun angle(point: Offset, center: Offset): Float =
    atan2(point.y - center.y, point.x - center.x)

private fun clockwiseDelta(before: Float, after: Float): Float {
    var delta = after - before
    if (delta > PI) delta -= (2 * PI).toFloat()
    if (delta < -PI) delta += (2 * PI).toFloat()
    return delta
}

/** Full-circle selector; buttons provide the same five-minute steps without a gesture. */
@Composable
fun FocusDurationDial(minutes: Int, onChange: (Int) -> Unit, color: Color) {
    val haptic = LocalHapticFeedback.current
    val change by rememberUpdatedState(onChange)
    val selectedMinutes by rememberUpdatedState(minutes)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularTimer(
            progress = minutes / 120f,
            timeText = stringResource(R.string.focus_minutes_value, minutes),
            arcColor = color,
            isWarning = false,
            size = 210.dp,
            modifier = Modifier.testTag("focus_duration_dial").pointerInput(Unit) {
                detectDragGestures { event, _ ->
                    val a = angle(event.position, Offset(size.width / 2f, size.height / 2f))
                    val turn = ((a + PI.toFloat() / 2f + 2f * PI.toFloat()) % (2f * PI.toFloat())) / (2f * PI.toFloat())
                    val selected = (5 + (turn * 23f).roundToInt() * 5).coerceIn(5, 120)
                    if (selected != selectedMinutes) { change(selected); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                    event.consume()
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = { change(FocusTimePolicy.selectionStep(minutes, -1)); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                enabled = minutes > TimerSettings.MIN_FOCUS_MINUTES, modifier = Modifier.testTag("focus_before_minus")) { Text("−5") }
            OutlinedButton(onClick = { change(FocusTimePolicy.selectionStep(minutes, 1)); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                enabled = minutes < TimerSettings.MAX_FOCUS_MINUTES, modifier = Modifier.testTag("focus_before_plus")) { Text("+5") }
        }
    }
}

/** Long press unlocks clockwise dial input; inactivity locks it again after three seconds. */
@Composable
fun FocusExtensionDial(state: TimerState, settings: TimerSettings, color: Color, timeText: String, onAdd: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val add by rememberUpdatedState(onAdd)
    var editing by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    var lastAngle by remember { mutableFloatStateOf(0f) }
    var clockwise by remember { mutableFloatStateOf(0f) }
    val blocked = FocusTimePolicy.blocked(state, settings)
    LaunchedEffect(editing, activity) {
        if (editing) { delay(3_000); editing = false }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularTimer(
            progress = state.progress, timeText = timeText, arcColor = color,
            isWarning = state.isWarning, size = 280.dp,
            modifier = Modifier.testTag("focus_extension_dial")
                .pointerInput(blocked) {
                    detectTapGestures(onPress = {
                        // The system long-press threshold is shorter than W6's one second.
                        val releasedEarly = withTimeoutOrNull(1_000) { tryAwaitRelease() }
                        if (releasedEarly == null && blocked == null) {
                            editing = true; activity++; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tryAwaitRelease()
                        }
                    })
                }
                .pointerInput(editing, blocked) {
                    detectDragGestures(onDragStart = { lastAngle = angle(it, Offset(size.width / 2f, size.height / 2f)); clockwise = 0f }) { event, _ ->
                        if (editing && blocked == null) {
                            val next = angle(event.position, Offset(size.width / 2f, size.height / 2f))
                            clockwise += clockwiseDelta(lastAngle, next).coerceAtLeast(0f)
                            lastAngle = next
                            if (clockwise >= PI.toFloat() / 6f) {
                                clockwise = 0f; activity++; add(); haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                        event.consume()
                    }
                },
        )
        if (editing) Text(stringResource(R.string.focus_dial_unlocked), modifier = Modifier.testTag("focus_dial_unlocked"))
        else Text(stringResource(R.string.focus_dial_locked), modifier = Modifier.testTag("focus_dial_locked"))
    }
}
