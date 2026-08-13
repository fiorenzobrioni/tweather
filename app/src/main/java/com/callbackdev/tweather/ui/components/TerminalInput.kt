package com.callbackdev.tweather.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Single-line terminal prompt input (`> Search Location _`): prompt glyph in gray,
 * typed text as a syntax string, blinking underscore cursor after the text. The
 * native caret is hidden — the underscore *is* the cursor.
 */
@Composable
fun TerminalInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = ">",
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    val syntax = TweatherTheme.syntax
    val textStyle = MaterialTheme.typography.bodySmall

    val blink = rememberInfiniteTransition(label = "cursor-blink")
    val cursorAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0 using LinearEasing
                1f at 499
                0f at 500
                0f at 999
            }
        ),
        label = "cursor-alpha"
    )

    BasicTextField(
        value = value,
        onValueChange = { onValueChange(it.replace("\n", "")) },
        modifier = modifier,
        textStyle = textStyle.copy(color = syntax.string),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = { innerTextField ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prompt.isNotEmpty()) {
                    Text(
                        text = prompt,
                        style = textStyle,
                        color = syntax.comment
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = syntax.comment.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
                Text(
                    text = "_",
                    style = textStyle,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.alpha(cursorAlpha)
                )
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun TerminalInputPreview() {
    TweatherTheme {
        var text by remember { mutableStateOf("") }
        TerminalInput(
            value = text,
            onValueChange = { text = it },
            placeholder = "Search Location",
            modifier = Modifier.padding(16.dp)
        )
    }
}
