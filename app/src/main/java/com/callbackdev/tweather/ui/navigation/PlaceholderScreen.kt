package com.callbackdev.tweather.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.Preview
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.TweatherTheme

/** Stub for the screens of later fasi — an empty fake file with a TODO comment. */
@Composable
fun PlaceholderScreen(fileName: String, phase: String) {
    val syntax = TweatherTheme.syntax
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = fileName)
            CodeCanvas(
                lines = listOf(
                    CodeLine(AnnotatedString("{", SpanStyle(color = syntax.comment))),
                    commentLine("// TODO: module not yet compiled", syntax, indent = 1),
                    commentLine("// scheduled: $phase", syntax, indent = 1),
                    CodeLine(AnnotatedString("}", SpanStyle(color = syntax.comment)))
                ),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun PlaceholderScreenPreview() {
    TweatherTheme {
        PlaceholderScreen(fileName = "search_query.json", phase = "Fase 6")
    }
}
