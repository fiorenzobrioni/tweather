package com.callbackdev.tweather.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.ui.components.CanvasLine
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.CodeLine
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.SyntaxText
import com.callbackdev.tweather.ui.components.TerminalInput
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.WidgetLine
import com.callbackdev.tweather.ui.components.commentLine
import com.callbackdev.tweather.ui.theme.SyntaxColors
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * Search screen: the fake file `search_query.json`. The `"search_term"` property IS
 * the input field (a [TerminalInput] embedded between JSON quotes); geocoding results
 * and the persisted `recent_searches` render as tappable JSON lines.
 */
@Composable
fun SearchScreen(
    onCitySelected: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recents by viewModel.recentSearches.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        recents = recents,
        onQueryChange = viewModel::onQueryChange,
        onSearchNow = viewModel::searchNow,
        onSelect = { viewModel.select(it); onCitySelected() },
        onRecent = viewModel::searchNow
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    recents: List<String>,
    onQueryChange: (String) -> Unit,
    onSearchNow: () -> Unit,
    onSelect: (City) -> Unit,
    onRecent: (String) -> Unit
) {
    val syntax = TweatherTheme.syntax
    val lines = remember(state, recents, syntax) {
        buildSearchLines(state, recents, syntax, onQueryChange, onSearchNow, onSelect, onRecent)
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = "search_query.json")
            CodeCanvas(
                lines = lines,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                Text("⎇ geocoding")
                StatusBarDivider()
                Text(
                    when {
                        state.isSearching -> stringResource(R.string.status_querying)
                        state.error != null -> stringResource(R.string.status_results, 0)
                        else -> stringResource(R.string.status_results, state.results.size)
                    }
                )
                Spacer(Modifier.weight(1f))
                Text("open-meteo.com")
            }
        }
    }
}

private fun buildSearchLines(
    state: SearchUiState,
    recents: List<String>,
    syntax: SyntaxColors,
    onQueryChange: (String) -> Unit,
    onSearchNow: () -> Unit,
    onSelect: (City) -> Unit,
    onRecent: (String) -> Unit
): List<CanvasLine> = buildList {
    add(commentLine("// Tweather Search Query", syntax))
    add(punctLine("{", 0, syntax))

    add(WidgetLine(indent = 1) {
        SearchTermLine(state.query, onQueryChange, onSearchNow)
    })

    val showResults = state.query.trim().length >= 2
    if (showResults) {
        add(keyOpenLine("results", "[", 1, syntax))
        if (state.isSearching) {
            add(commentLine("// GET /v1/search?name=${state.query.trim()}", syntax, indent = 2))
        }
        state.error?.let {
            add(commentLine("// ERROR: ${it.terminalMessage}", syntax, indent = 2))
        }
        state.results.forEachIndexed { i, city ->
            add(
                CodeLine(
                    text = cityResultText(city, trailingComma = i != state.results.lastIndex, syntax),
                    indent = 2,
                    onClick = { onSelect(city) }
                )
            )
        }
        add(punctLine("],", 1, syntax))
    }

    add(keyOpenLine("recent_searches", "[", 1, syntax))
    if (recents.isEmpty()) {
        add(commentLine("// empty", syntax, indent = 2))
    }
    recents.forEachIndexed { i, term ->
        add(
            CodeLine(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = syntax.string)) { append("\"$term\"") }
                    if (i != recents.lastIndex) {
                        withStyle(SpanStyle(color = syntax.comment)) { append(",") }
                    }
                },
                indent = 2,
                onClick = { onRecent(term) }
            )
        )
    }
    add(punctLine("]", 1, syntax))

    add(punctLine("}", 0, syntax))
}

/** `"search_term": "<input>_",` — the input field disguised as a JSON value. */
@Composable
private fun SearchTermLine(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchNow: () -> Unit
) {
    val syntax = TweatherTheme.syntax
    Row(verticalAlignment = Alignment.CenterVertically) {
        SyntaxText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.key)) { append("\"search_term\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(": ") }
                withStyle(SpanStyle(color = syntax.string)) { append("\"") }
            }
        )
        TerminalInput(
            value = query,
            onValueChange = onQueryChange,
            prompt = "",
            placeholder = stringResource(R.string.search_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchNow() })
        )
        SyntaxText(
            buildAnnotatedString {
                withStyle(SpanStyle(color = syntax.string)) { append("\"") }
                withStyle(SpanStyle(color = syntax.comment)) { append(",") }
            }
        )
    }
}

/** `{ "city": "Milan", "region": "Lombardy", "country": "Italy" },` */
private fun cityResultText(
    city: City,
    trailingComma: Boolean,
    syntax: SyntaxColors
): AnnotatedString = buildAnnotatedString {
    fun punct(s: String) = withStyle(SpanStyle(color = syntax.comment)) { append(s) }
    fun entry(key: String, value: String) {
        withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
        punct(": ")
        withStyle(SpanStyle(color = syntax.string)) { append("\"$value\"") }
    }
    punct("{ ")
    entry("city", city.name)
    city.region?.let { punct(", "); entry("region", it) }
    city.country?.let { punct(", "); entry("country", it) }
    punct(" }")
    if (trailingComma) punct(",")
}

private fun punctLine(text: String, indent: Int, syntax: SyntaxColors) =
    CodeLine(AnnotatedString(text, SpanStyle(color = syntax.comment)), indent)

private fun keyOpenLine(key: String, bracket: String, indent: Int, syntax: SyntaxColors) =
    CodeLine(
        buildAnnotatedString {
            withStyle(SpanStyle(color = syntax.key)) { append("\"$key\"") }
            withStyle(SpanStyle(color = syntax.comment)) { append(": $bracket") }
        },
        indent
    )

@Preview(showBackground = true, backgroundColor = 0xFF10141A, heightDp = 600)
@Composable
private fun SearchScreenPreview() {
    TweatherTheme {
        SearchScreen(
            state = SearchUiState(
                query = "mil",
                results = listOf(
                    City(3_173_435, "Milan", "Lombardy", "Italy", Coordinates(45.46, 9.19), "Europe/Rome"),
                    City(4_164_138, "Milford", "Connecticut", "USA", Coordinates(41.22, -73.06), "America/New_York")
                )
            ),
            recents = listOf("Seattle, WA", "London, England", "Reykjavik, Iceland"),
            onQueryChange = {},
            onSearchNow = {},
            onSelect = {},
            onRecent = {}
        )
    }
}
