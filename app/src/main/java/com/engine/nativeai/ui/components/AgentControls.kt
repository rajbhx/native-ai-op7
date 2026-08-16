import com.engine.nativeai.ui.PillButton
package com.engine.nativeai.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

@Composable
fun AgentControls(
    running: Boolean,
    hasContent: Boolean,
    onAgent: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        PillButton(
            if (running) "Stop" else "Agent",
            Modifier.weight(1f),
            primary = true,
            enabled = true,
            loading = running,
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (running) onStop() else onAgent()
        }
        Spacer(Modifier.width(8.dp))
        PillButton(
            "Clear",
            Modifier.weight(1f),
            enabled = !running && hasContent,
        ) {
            onClear()
        }
    }
    Spacer(Modifier.height(12.dp))
}
