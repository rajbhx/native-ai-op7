package com.engine.nativeai.ui.components

import com.engine.nativeai.ui.ValuePill

import com.engine.nativeai.ui.PillButton

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.engine.nativeai.ui.OpBg
import com.engine.nativeai.ui.OpRed
import com.engine.nativeai.ui.OpText
import com.engine.nativeai.ui.OpTextSecondary

@Composable
fun QuickActionBar(
    hasModel: Boolean,
    onPickModel: () -> Unit,
    onImportGGUF: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PillButton("Pick model", Modifier.weight(1f), primary = !hasModel) { onPickModel() }
        PillButton("Import GGUF", Modifier.weight(1f)) { onImportGGUF() }
        PillButton("Download", Modifier.weight(1f)) { onDownload() }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun RoutingModeBar(
    modes: List<String>,
    selectedIndex: Int,
    onModeSelected: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OpBg, RoundedCornerShape(20.dp))
            .padding(2.dp),
    ) {
        modes.forEachIndexed { i, label ->
            ValuePill(label, selected = i == selectedIndex, modifier = Modifier.weight(1f)) {
                onModeSelected(i)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
