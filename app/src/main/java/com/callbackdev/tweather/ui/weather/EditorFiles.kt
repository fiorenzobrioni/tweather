package com.callbackdev.tweather.ui.weather

import com.callbackdev.tweather.data.MainEditorFile

/**
 * The editor strip's file names, and the mapping between a tab index and a
 * [MainEditorFile] (Fase 16c).
 *
 * The strip is two names long or three, depending on `sky.enabled`, so the index a
 * tap reports is not a fixed meaning — it has to be read back through the same list
 * that drew it. Keeping both directions in one place is what stops the strip and the
 * screen disagreeing about which file tab 2 is.
 */
const val JsonFileName = "weather_data.json"
const val ReadmeFileName = "README.md"
const val SkyFileName = "sky.crontab"

fun editorFiles(skyEnabled: Boolean): List<String> =
    if (skyEnabled) listOf(JsonFileName, ReadmeFileName, SkyFileName)
    else listOf(JsonFileName, ReadmeFileName)

fun MainEditorFile.fileName(): String = when (this) {
    MainEditorFile.JSON -> JsonFileName
    MainEditorFile.README -> ReadmeFileName
    MainEditorFile.SKY -> SkyFileName
}

/** The file at [index] of [editorFiles], or the JSON when the index is stale. */
fun editorFileAt(index: Int, skyEnabled: Boolean): MainEditorFile =
    when (editorFiles(skyEnabled).getOrNull(index)) {
        ReadmeFileName -> MainEditorFile.README
        SkyFileName -> MainEditorFile.SKY
        else -> MainEditorFile.JSON
    }

/**
 * The file actually shown. A persisted `SKY` selection outlives switching the module
 * off — the enum keeps the value so the tab comes back where it was — but it must
 * not keep SHOWING a tab the strip no longer draws.
 */
fun MainEditorFile.visible(skyEnabled: Boolean): MainEditorFile =
    if (this == MainEditorFile.SKY && !skyEnabled) MainEditorFile.JSON else this
