package com.engine.nativeai.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/** OxygenOS "NEVER SETTLE" design tokens (blueprint Phase 6). */
val OpRed = Color(0xFFEB0029)
val OpRedDark = Color(0xFF8F0019)
val OpBg = Color(0xFF121212)
val OpCard = Color(0xFF1E1E1E)
val OpText = Color(0xFFFFFFFF)
val OpTextSecondary = Color(0xFFA0A0A0)
val OpDivider = Color(0xFF2A2A2A)
val OpBorder = Color(0xFF3A3A3A)
val OpBlue = Color(0xFF008BFF)
val OpAmber = Color(0xFFF5A623)
val OpSuccess = Color(0xFF2ECC71)

/** Semantic roles: red = exactly one primary action per screen + danger. */
val OpPrimaryAction = OpRed
val OpStatusSuccess = OpSuccess
val OpStatusWarn = OpAmber
val OpStatusInfo = OpBlue
val OpStatusDanger = OpRed
val OpLinkAccent = OpBlue

private val OpColors = darkColorScheme(
    primary = OpRed,
    onPrimary = Color.White,
    secondary = OpBlue,
    onSecondary = Color.White,
    background = OpBg,
    onBackground = OpText,
    surface = OpCard,
    onSurface = OpText,
    surfaceVariant = OpCard,
    onSurfaceVariant = OpTextSecondary,
    outline = OpDivider,
)

@Composable
internal fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) OpRed else OpCard,
            contentColor = OpText,
            disabledContainerColor = OpCard.copy(alpha = 0.4f),
            disabledContentColor = OpTextSecondary,
        ),
        border = if (primary) null else BorderStroke(1.dp, OpDivider),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = if (primary) Color.White else OpTextSecondary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(text)
        }
    }
}


@Composable
internal fun ValuePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) OpText else OpTextSecondary,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .background(
                if (selected) OpCard else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            .border(
                BorderStroke(1.dp, if (selected) OpBorder else OpDivider),
                RoundedCornerShape(20.dp),
            )
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}


@Composable
fun OxygenOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OpColors, content = content)
}
