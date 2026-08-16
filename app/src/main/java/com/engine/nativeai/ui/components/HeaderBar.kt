package com.engine.nativeai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.EngineUiState
import com.engine.nativeai.ui.OpAmber
import com.engine.nativeai.ui.OpRed
import com.engine.nativeai.ui.OpSuccess
import com.engine.nativeai.ui.OpText
import com.engine.nativeai.ui.OpTextSecondary

@Composable
fun HeaderBar(
    headerState: EngineUiState,
    running: Boolean,
    elapsed: Long,
    formatElapsed: (Long) -> String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("NEVER SETTLE", color = OpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Native Agentic AI \u00b7 OnePlus 7", color = OpTextSecondary, fontSize = 12.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { stateDescription = "Engine state ${headerState.label}" },
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            when (headerState) {
                                EngineUiState.COMPLETED -> OpSuccess
                                EngineUiState.ERROR -> OpRed
                                EngineUiState.OFFLINE -> OpTextSecondary
                                else -> OpAmber
                            },
                            RoundedCornerShape(4.dp),
                        ),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (running) "${headerState.label} \u00b7 ${formatElapsed(elapsed)}" else headerState.label,
                    color = OpText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("OP7", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("SD855 \u00b7 6-8 GB", color = OpTextSecondary, fontSize = 9.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
}
