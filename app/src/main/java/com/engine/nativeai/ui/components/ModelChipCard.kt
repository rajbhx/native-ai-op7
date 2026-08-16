package com.engine.nativeai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.ModelDescriptor
import com.engine.nativeai.ui.OpAmber
import com.engine.nativeai.ui.OpBorder
import com.engine.nativeai.ui.OpCard
import com.engine.nativeai.ui.OpRed
import com.engine.nativeai.ui.OpText
import com.engine.nativeai.ui.OpTextSecondary

@Composable
fun ModelChipCard(
    selected: ModelDescriptor?,
    hasModelFile: Boolean,
    hasApiKey: Boolean,
    status: String,
    importing: Boolean,
    importStatus: String,
    importProgress: Float?,
    summary: String,
    onOpenSettings: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(OpCard, RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, OpBorder), RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Engine settings" }
            .clickable { onOpenSettings() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            if (selected == null) "Model \u00b7 none selected" else "Model \u00b7 ${selected.displayName}",
            color = OpText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (selected == null) "tap to choose" else summary,
            color = OpTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text("\u2699", color = OpTextSecondary, fontSize = 14.sp)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        status,
        color = OpTextSecondary,
        fontSize = 12.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (importing) {
        Text(importStatus, color = OpAmber, fontSize = 11.sp)
        importProgress?.let { p ->
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { p },
                color = OpRed,
                trackColor = OpBorder,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
