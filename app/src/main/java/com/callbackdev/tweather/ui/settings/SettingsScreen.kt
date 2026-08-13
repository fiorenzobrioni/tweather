package com.callbackdev.tweather.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.data.EditorSettings
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.CodeToggle
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.SyntaxText
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.WidgetLine
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Settings screen: the fake file `settings.config`, mockup format (JSON body with
 * `//` comments). For now only the `"editor"` section is live — booleans are
 * [CodeToggle]s flipped in place; units/theme/notifications land with Fase 7.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val editor by viewModel.editorSettings.collectAsStateWithLifecycle()
    SettingsScreen(
        editor = editor,
        onLineNumbers = viewModel::setLineNumbers,
        onWordWrap = viewModel::setWordWrap
    )
}

@Composable
fun SettingsScreen(
    editor: EditorSettings,
    onLineNumbers: (Boolean) -> Unit,
    onWordWrap: (Boolean) -> Unit
) {
    val syntax = TweatherTheme.syntax
    val lines = buildSettingsLines(editor, syntax, onLineNumbers, onWordWrap)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = "settings.config")
            CodeCanvas(
                lines = lines,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                Text("⎇ config")
                StatusBarDivider()
                Text("rw")
                Spacer(Modifier.weight(1f))
                Text("UTF-8")
            }
        }
    }
}

private fun buildSettingsLines(
    editor: EditorSettings,
    syntax: SyntaxColors,
    onLineNumbers: (Boolean) -> Unit,
    onWordWrap: (Boolean) -> Unit
): List<CanvasLine> = buildList {
    add(commentLine("// Tweather Configuration File", syntax))
    add(punctLine("{", 0, syntax))
    add(keyOpenLine("editor", 1, syntax))
    add(WidgetLine(indent = 2) {
        ToggleLine("line_numbers", editor.lineNumbers, trailingComma = true, hint = true, onLineNumbers)
    })
    add(WidgetLine(indent = 2) {
        ToggleLine("word_wrap", editor.wordWrap, trailingComma = false, hint = false, onWordWrap)
    })
    add(punctLine("}", 1, syntax))
    add(commentLine("// units, theme, notifications: Fase 7", syntax, indent = 1))
    add(punctLine("}", 0, syntax))
}

/** `"word_wrap": false,` where the boolean is a tappable [CodeToggle]. */
@Composable
private fun ToggleLine(
    key: String,
    value: Boolean,
    trailingComma: Boolean,
    hint: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    val syntax = TweatherTheme.syntax
    Row(verticalAlignment = Alignment.CenterVertically) {
        SyntaxText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
            }
        )
        CodeToggle(value = value, onValueChange = onValueChange)
        SyntaxText(
            buildAnnotatedString {
                if (trailingComma) withStyle(SpanStyle(color = syntax.comment)) { append(",") }
                if (hint) {
                    withStyle(SpanStyle(color = syntax.comment.copy(alpha = 0.6f))) {
                        append("  // click to toggle")
                    }
                }
            }
        )
    }
}

private fun punctLine(text: String, indent: Int, syntax: SyntaxColors) =
    CodeLine(AnnotatedString(text, SpanStyle(color = syntax.comment)), indent)

private fun keyOpenLine(key: String, indent: Int, syntax: SyntaxColors) =
    CodeLine(
        buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
            withStyle(SpanStyle(color = syntax.comment)) { append(": {") }
        },
        indent
    )

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun SettingsScreenPreview() {
    TweatherTheme {
        SettingsScreen(
            editor = EditorSettings(lineNumbers = true, wordWrap = false),
            onLineNumbers = {},
            onWordWrap = {}
        )
    }
}
