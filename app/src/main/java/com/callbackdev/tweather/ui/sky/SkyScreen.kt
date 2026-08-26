package com.callbackdev.tweather.ui.sky

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.callbackdev.tweather.R
import com.callbackdev.tweather.ui.components.CodeCanvas
import com.callbackdev.tweather.ui.components.EditorTabs
import com.callbackdev.tweather.ui.components.StatusBarDivider
import com.callbackdev.tweather.ui.components.StatusBarStart
import com.callbackdev.tweather.ui.components.StatusBarText
import com.callbackdev.tweather.ui.components.TerminalStatusBar
import com.callbackdev.tweather.ui.theme.TweatherTheme

/**
 * The `sky.crontab` tab of the editor strip (Fase 16c). Its own screen rather than a
 * third branch inside `WeatherScreen`, the same way `alerts.rules` and `HELP.md` are
 * their own screens behind the Settings strip: the weather screen is built around a
 * report that can be loading, stale or missing, and none of those states mean
 * anything to a file computed from a latitude.
 */
@Composable
fun SkyScreen(
    editorFiles: List<String>,
    activeIndex: Int,
    onSelectFile: (Int) -> Unit,
    canvasState: LazyListState = rememberLazyListState(),
    viewModel: SkyViewModel = viewModel(factory = SkyViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SkyScreen(
        state = state,
        editorFiles = editorFiles,
        activeIndex = activeIndex,
        onSelectFile = onSelectFile,
        actions = SkyActions(
            onToggleEnabled = viewModel::toggleEnabled,
            onRemove = viewModel::remove,
            onAdd = viewModel::add
        ),
        canvasState = canvasState
    )
}

@Composable
fun SkyScreen(
    state: SkyUiState,
    editorFiles: List<String>,
    activeIndex: Int,
    onSelectFile: (Int) -> Unit,
    actions: SkyActions,
    canvasState: LazyListState = rememberLazyListState()
) {
    val syntax = TweatherTheme.syntax
    val labels = rememberSkyLabels()
    val editing = rememberSkyEdit()
    val lines = buildSkyLines(
        state = state,
        editing = editing.value,
        syntax = syntax,
        actions = actions,
        labels = labels,
        onStartEdit = { editing.value = it }
    )
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            EditorTabs(
                fileNames = editorFiles,
                activeIndex = activeIndex,
                onSelect = onSelectFile
            )
            CodeCanvas(
                lines = lines,
                state = canvasState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            )
            TerminalStatusBar {
                StatusBarStart {
                    // No `⎇ <city>` switcher here even though this file IS per city:
                    // the branch glyph on the weather tabs jumps to cities.json, and
                    // a second one two tabs away would be the same control in two
                    // places. The city name is in the file's own header line.
                    StatusBarText("⎇ sky", shrink = true)
                    StatusBarDivider()
                    StatusBarText(
                        stringResource(R.string.status_sky_jobs, state.subscriptions.size)
                    )
                }
                Spacer(Modifier.weight(1f))
                StatusBarText("UTF-8")
            }
        }
    }
}
