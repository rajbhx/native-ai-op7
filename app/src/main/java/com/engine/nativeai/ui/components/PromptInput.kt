package com.engine.nativeai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.engine.nativeai.ui.OpBorder
import com.engine.nativeai.ui.OpCard
import com.engine.nativeai.ui.OpRed
import com.engine.nativeai.ui.OpText
import com.engine.nativeai.ui.OpTextSecondary

@Composable
fun PromptInput(
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("What do you want me to do?", color = OpTextSecondary) },
            minLines = 2,
            maxLines = 6,
            isError = false,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend()
            }),
            trailingIcon = {
                if (prompt.isNotEmpty()) {
                    Text(
                        "\u00d7",
                        color = OpTextSecondary,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .semantics { contentDescription = "Clear prompt" }
                            .clickable { onPromptChange("") }
                            .padding(8.dp),
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OpRed,
                unfocusedBorderColor = OpBorder,
                errorBorderColor = OpRed,
                cursorColor = OpRed,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSend()
            },
            enabled = prompt.isNotBlank() && enabled,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OpCard,
                contentColor = OpText,
                disabledContainerColor = OpCard.copy(alpha = 0.4f),
                disabledContentColor = OpTextSecondary,
            ),
            border = BorderStroke(1.dp, OpBorder),
            modifier = Modifier
                .height(56.dp)
                .semantics { contentDescription = "Send prompt" },
        ) {
            Text("Send", fontWeight = FontWeight.Bold)
        }
    }
    Text(
        "\u2191 Send \u00b7 quick completion with selected model \u2014 Agent runs the full tool loop",
        color = OpTextSecondary,
        fontSize = 10.sp,
        modifier = Modifier.padding(top = 4.dp, start = 2.dp),
    )
}
