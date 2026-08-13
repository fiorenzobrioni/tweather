package com.callbackdev.tweather.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Checkbox rendered as code: `[x]` / `[ ]` in monospace, optional label. Controls
 * are text per the design system — no native Material checkbox.
 */
@Composable
fun CodeCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null
) {
    val syntax = TweatherTheme.syntax
    Row(
        modifier = modifier.toggleable(
            value = checked,
            role = Role.Checkbox,
            onValueChange = onCheckedChange
        ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.comment)) { append("[") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primaryContainer)) {
                    append(if (checked) "x" else " ")
                }
                withStyle(SpanStyle(color = syntax.comment)) { append("]") }
            },
            style = MaterialTheme.typography.bodySmall
        )
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Boolean config value rendered as tappable `true` / `false` in the number/boolean
 * token color (settings mockup: booleans toggle on tap).
 */
@Composable
fun CodeToggle(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = if (value) "true" else "false",
        style = MaterialTheme.typography.bodySmall,
        color = TweatherTheme.syntax.number,
        modifier = modifier.toggleable(
            value = value,
            role = Role.Switch,
            onValueChange = onValueChange
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun CodeControlsPreview() {
    TweatherTheme {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            var checked by remember { mutableStateOf(true) }
            CodeCheckbox(
                checked = checked,
                onCheckedChange = { checked = it },
                label = "severe_weather_alerts"
            )
            Spacer(Modifier.width(24.dp))
            var enabled by remember { mutableStateOf(false) }
            CodeToggle(value = enabled, onValueChange = { enabled = it })
        }
    }
}
