package com.callbackdev.chiaro.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN.md §2.1: a composable names a ROLE, never a hex. The rule is worth a sweep
 * rather than a code review, because breaking it is not a decision anybody makes on
 * purpose — it is what happens at 23:00 when a color is nearly right and the theme file
 * is two directories away.
 *
 * `ui/theme/` is where the hexes live, so it is the one exception.
 */
class NoRawColorTest {

    private val literal = Regex("""Color\(0x|"#[0-9a-fA-F]{6}""")

    @Test
    fun `no color literal outside the theme package`() {
        val root = File("src/main/kotlin/com/callbackdev/chiaro")
        assertTrue("the sweep found no sources to read at ${root.absolutePath}", root.isDirectory)

        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.replace(File.separatorChar, '/').contains("/ui/theme/") }
            .flatMap { file ->
                file.readLines().withIndex()
                    .filter { (_, line) -> literal.containsMatchIn(line) }
                    .map { (i, line) -> "${file.path}:${i + 1}  ${line.trim()}" }
            }
            .toList()

        assertTrue(
            "a color literal belongs in ui/theme/, not here:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }
}
