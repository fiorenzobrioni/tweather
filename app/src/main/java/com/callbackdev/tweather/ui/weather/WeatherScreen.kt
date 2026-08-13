package com.callbackdev.tweather.ui.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.GlowFab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.buildJsonLines
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Main screen: the live weather report rendered as the fake source file
 * `weather_data.json` — editor tab on top, code canvas with gutter and syntax
 * highlighting, glowing refresh FAB, terminal status bar at the bottom.
 */
@Composable
fun WeatherScreen(
    onOpenExplorer: () -> Unit = {},
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    WeatherScreen(state = state, onRefresh = viewModel::refresh, onOpenExplorer = onOpenExplorer)
}

@Composable
fun WeatherScreen(state: WeatherUiState, onRefresh: () -> Unit, onOpenExplorer: () -> Unit = {}) {
    val syntax = TweatherTheme.syntax
    val resources = LocalContext.current.resources
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val lines = remember(state.report, state.isLoading, state.error, syntax, locale) {
        buildScreenLines(state, syntax, WeatherTranslations.translator(resources), locale)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = "weather_data.json") {
                Text(
                    text = "[ files ]",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.cd_open_explorer)
                        ) { onOpenExplorer() }
                        .padding(8.dp)
                )
            }
            Box(Modifier.weight(1f)) {
                CodeCanvas(lines = lines, modifier = Modifier.fillMaxSize())
                GlowFab(
                    onClick = onRefresh,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 24.dp),
                    contentDescription = stringResource(R.string.cd_refresh),
                    icon = { RefreshIcon(spinning = state.isLoading) }
                )
            }
            WeatherStatusBar(state)
        }
    }
}

/** The FAB's refresh glyph doubles as loading indicator: it spins during a fetch. */
@Composable
private fun RefreshIcon(spinning: Boolean) {
    val angle: Float
    if (spinning) {
        val transition = rememberInfiniteTransition(label = "refresh-spin")
        angle = transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "refresh-angle"
        ).value
    } else {
        angle = 0f
    }
    Icon(
        Icons.Filled.Refresh,
        contentDescription = null,
        modifier = Modifier.graphicsLayer { rotationZ = angle }
    )
}

@Composable
private fun WeatherStatusBar(state: WeatherUiState) {
    val report = state.report
    TerminalStatusBar {
        Text("⎇ ${report?.location?.city ?: "—"}")
        StatusBarDivider()
        Text(
            when {
                state.error != null -> "api: ERR"
                report != null -> "api: 200 OK"
                else -> "api: …"
            },
            color = if (state.error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.weight(1f))
        Text(
            when {
                state.isLoading -> stringResource(R.string.status_syncing)
                report != null -> stringResource(
                    R.string.status_last_updated,
                    report.systemInfo.lastSync
                        .atZone(runCatching { ZoneId.of(report.location.timezone) }
                            .getOrDefault(ZoneId.systemDefault()))
                        .format(LastUpdated)
                )
                else -> stringResource(R.string.status_last_updated, "—")
            }
        )
    }
}

private val LastUpdated = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The document shown in the canvas. Loading and errors are part of the fake file,
 * rendered as `//` comment lines above the last good JSON (which survives a failed
 * refresh). Comments and errors are code — English by design.
 */
private fun buildScreenLines(
    state: WeatherUiState,
    syntax: SyntaxColors,
    translate: (String) -> String,
    locale: Locale
): List<CodeLine> = buildList {
    if (state.isLoading) {
        add(commentLine("// fetching weather_data.json …", syntax))
        add(commentLine("// GET https://api.open-meteo.com/v1/forecast", syntax))
    }
    state.error?.let {
        add(commentLine("// ERROR: ${it.terminalMessage}", syntax))
        add(commentLine("// hint: tap ( ↻ ) to retry", syntax))
    }
    state.report?.let {
        if (isNotEmpty()) add(CodeLine(AnnotatedString("")))
        addAll(buildJsonLines(it.toDisplayJson(translate, locale), syntax))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 800)
@Composable
private fun WeatherScreenPreview() {
    TweatherTheme {
        WeatherScreen(
            state = WeatherUiState(report = sampleWeatherReport(), isLoading = false),
            onRefresh = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun WeatherScreenLoadingPreview() {
    TweatherTheme {
        WeatherScreen(state = WeatherUiState(isLoading = true), onRefresh = {})
    }
}
