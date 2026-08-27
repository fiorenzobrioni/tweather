package com.callbackdev.tweather.ui.weather

import com.callbackdev.tweather.data.MainEditorFile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The editor strip is two names long or three depending on `sky.enabled` (Fase 16c),
 * so a tab index has no fixed meaning — it has to be read back through the same list
 * that drew it. These are the tests that keep the strip and the screen from
 * disagreeing about which file tab 2 is.
 */
class EditorFilesTest {

    @Test
    fun `the strip grows a third name only when the module is on`() {
        assertEquals(
            listOf("weather_data.json", "README.md"),
            editorFiles(skyEnabled = false)
        )
        assertEquals(
            listOf("weather_data.json", "README.md", "sky.crontab"),
            editorFiles(skyEnabled = true)
        )
    }

    @Test
    fun `an index round-trips to the file it drew`() {
        listOf(true, false).forEach { skyEnabled ->
            editorFiles(skyEnabled).forEachIndexed { index, name ->
                assertEquals(name, editorFileAt(index, skyEnabled).fileName())
            }
        }
    }

    @Test
    fun `an index the strip cannot have falls back to the document`() {
        // Tab 2 exists only with the module on. Off, the same index is stale state,
        // not a third file.
        assertEquals(MainEditorFile.JSON, editorFileAt(2, skyEnabled = false))
        assertEquals(MainEditorFile.SKY, editorFileAt(2, skyEnabled = true))
        assertEquals(MainEditorFile.JSON, editorFileAt(9, skyEnabled = true))
    }

    /**
     * A persisted `SKY` selection outlives switching the module off — the enum keeps
     * the value so the tab comes back where the user left it — but it must not keep
     * SHOWING a tab the strip no longer draws.
     */
    @Test
    fun `a sky selection survives the module being switched off without showing`() {
        assertEquals(MainEditorFile.JSON, MainEditorFile.SKY.visible(skyEnabled = false))
        assertEquals(MainEditorFile.SKY, MainEditorFile.SKY.visible(skyEnabled = true))
        assertEquals(MainEditorFile.README, MainEditorFile.README.visible(skyEnabled = false))
    }
}
