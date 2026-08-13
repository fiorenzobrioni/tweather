package com.callbackdev.tweather.ui.explorer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.data.CityStore
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.Coordinates
import com.callbackdev.tweather.ui.components.EditorTab
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.components.TreeViewItem
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * The Explorer tab's city browser: saved cities as `.json` files in a tree view.
 * Tapping a file makes that city active (and returns to the editor); `[rm]` deletes
 * it; the trailing `+ add_city…` entry jumps to the Search tab.
 */
@Composable
fun ExplorerScreen(
    onCitySelected: () -> Unit,
    onAddCity: () -> Unit,
    viewModel: ExplorerViewModel = viewModel(factory = ExplorerViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ExplorerScreen(
        state = state,
        onSelect = { viewModel.select(it); onCitySelected() },
        onRemove = viewModel::remove,
        onAddCity = onAddCity
    )
}

@Composable
fun ExplorerScreen(
    state: ExplorerUiState,
    onSelect: (City) -> Unit,
    onRemove: (City) -> Unit,
    onAddCity: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTab(fileName = "explorer")
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                TreeViewItem(label = "~/tweather/cities/") {
                    state.cities.forEach { city ->
                        CityFileRow(
                            city = city,
                            isActive = city.id == state.activeCity?.id,
                            removable = state.cities.size > 1,
                            onSelect = { onSelect(city) },
                            onRemove = { onRemove(city) }
                        )
                    }
                    AddCityRow(onAddCity)
                }
            }
            TerminalStatusBar {
                Text("⎇ ${state.activeCity?.name ?: "—"}")
                StatusBarDivider()
                Text("${state.cities.size} file")
            }
        }
    }
}

/** A saved city rendered as a tree leaf: `· milan.json  // active   [rm]`. */
@Composable
private fun CityFileRow(
    city: City,
    isActive: Boolean,
    removable: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    Row(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClickLabel = "Open ${city.label}") { onSelect() }
        ) {
            Text(
                text = "·",
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(20.dp)
            )
            Text(
                text = "${city.fileSlug()}.json",
                style = style,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (isActive) {
                Text(text = "  // active", style = style, color = syntax.comment)
            }
        }
        if (removable) {
            Text(
                text = "[rm]",
                style = style,
                color = syntax.diffDel,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = "Remove ${city.label}") {
                        onRemove()
                    }
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun AddCityRow(onAddCity: () -> Unit) {
    val syntax = TweatherTheme.syntax
    val style = MaterialTheme.typography.bodySmall
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Add city") { onAddCity() }
    ) {
        Text(
            text = "+",
            style = style,
            color = syntax.diffAdd,
            modifier = Modifier.width(20.dp)
        )
        Text(text = "add_city…", style = style, color = syntax.diffAdd)
        Text(text = "  // opens search", style = style, color = syntax.comment)
    }
}

/** `New York` → `new_york`, the fake filename shown in the tree. */
private fun City.fileSlug(): String =
    name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "city" }

@Preview(showBackground = true, backgroundColor = 0xFF10141A)
@Composable
private fun ExplorerScreenPreview() {
    TweatherTheme {
        val milan = City(3_173_435, "Milan", "Lombardy", "Italy", Coordinates(45.46, 9.19), "Europe/Rome")
        ExplorerScreen(
            state = ExplorerUiState(
                cities = listOf(CityStore.DefaultCity, milan),
                activeCity = milan
            ),
            onSelect = {},
            onRemove = {},
            onAddCity = {}
        )
    }
}
