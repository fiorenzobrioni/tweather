package com.callbackdev.tweather.ui.sky

import com.callbackdev.tweather.data.SkySubscription
import java.time.format.DateTimeFormatter

/**
 * The home widget's one optional sky line (Fase 16e):
 *
 * ```
 * next: sun.set 20:12   ✓ pass
 * ```
 *
 * Off by default and last in the widget's line budget, which means it is the first
 * line dropped when the launcher gives the widget less room. That order is the point:
 * the temperature is why a weather widget exists, and this line is not.
 *
 * It repaints on the same fetch commit as everything else and costs no extra battery
 * — the schedule is local arithmetic and the verdict reads the forecast already in
 * hand.
 */
object SkyWidgetLine {

    private val ClockTime = DateTimeFormatter.ofPattern("HH:mm")

    /** Null when nothing is subscribed, or when there is no next job to name. */
    fun of(subscriptions: List<SkySubscription>, context: SkyContext): String? {
        val document = SkyDocumentBuilder.build(subscriptions, context)
        val next = document.rows
            .filter { it.enabled && it.at != null }
            .minByOrNull { it.at!! }
            ?: return null
        return buildString {
            append("next: ").append(next.job.id).append(" ")
            append(next.at!!.atZone(context.zone).format(ClockTime))
            // Only the glyph and the word, never the cloud number: at widget width
            // the line is ellipsized from the right, and the number would be the
            // first thing to go while taking the verdict's own word with it.
            next.verdict?.takeIf { it.isKnown }?.let {
                append("   ").append(it.kind.glyph).append(" ").append(it.kind.word)
            }
        }
    }
}
