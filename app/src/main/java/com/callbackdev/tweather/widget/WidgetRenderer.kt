package com.callbackdev.tweather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.callbackdev.tweather.MainActivity
import com.callbackdev.tweather.R

/**
 * Builds the RemoteViews for one render pass. [sizeMap] returns the API 31+
 * sizes-map RemoteViews: the launcher picks the best-fitting tier by itself on
 * every resize (and per orientation), with no round-trip to the provider.
 * Per-token colors travel as [ForegroundColorSpan]s — a ParcelableSpan, safe
 * across the RemoteViews IPC; fonts stay in the XML (typeface spans don't parcel).
 */
object WidgetRenderer {

    private val LineIds = listOf(
        R.id.widget_line1, R.id.widget_line2, R.id.widget_line3,
        R.id.widget_line4, R.id.widget_line5, R.id.widget_line6,
        R.id.widget_line7, R.id.widget_line8, R.id.widget_line9
    )
    /** How many body slots each tier fills; the rest are hidden. */
    internal fun lineCount(tier: WidgetTier): Int = when (tier) {
        WidgetTier.SMALL -> 0
        WidgetTier.MEDIUM -> 4
        WidgetTier.EXTENDED -> 5
        WidgetTier.LARGE -> LineIds.size
    }

    // Measured off the layouts (15sp line + 3dp lineSpacingExtra; 22sp temp over a
    // 12sp city in the small tier) — keep in sync if the styles change.
    private const val ChromeHeightDp = 73f
    private const val BodyLineHeightDp = 23f
    private const val SmallMinHeightDp = 52f

    /** Width the trailing ↻/cursor overlay needs on the last visible body line. */
    private const val RefreshGutterDp = 56f

    fun sizeMap(
        context: Context,
        content: (WidgetTier) -> WidgetContent,
        palette: WidgetPalette,
        opacityPct: Int
    ): RemoteViews = RemoteViews(
        breakpoints().entries.associate { (tier, size) ->
            size to render(context, content(tier), palette, opacityPct, tier)
        }
    )

    /**
     * A sizes-map key is a promise that the layout FITS in that many dp — the host
     * clips silently otherwise, so the heights are derived from the layouts instead
     * of guessed: fixed chrome (header 34 + divider 1 + prompt 38) + one body line
     * per slot + the body's bottom padding. Below the smallest key the launcher
     * falls back to it, so a minimum-size widget still gets SMALL.
     */
    internal fun minHeightDp(lines: Int): Float = ChromeHeightDp + lines * BodyLineHeightDp + 12f

    internal fun breakpoints(): Map<WidgetTier, SizeF> = mapOf(
        WidgetTier.SMALL to SizeF(110f, SmallMinHeightDp),
        WidgetTier.MEDIUM to SizeF(160f, minHeightDp(lineCount(WidgetTier.MEDIUM))),
        WidgetTier.EXTENDED to SizeF(160f, minHeightDp(lineCount(WidgetTier.EXTENDED))),
        WidgetTier.LARGE to SizeF(160f, minHeightDp(lineCount(WidgetTier.LARGE)))
    )

    internal fun render(
        context: Context,
        content: WidgetContent,
        palette: WidgetPalette,
        opacityPct: Int,
        tier: WidgetTier
    ): RemoteViews {
        val views = RemoteViews(context.packageName, layoutFor(tier))

        views.setInt(R.id.widget_bg_fill, "setColorFilter", palette.background)
        // setImageAlpha masks with 0xFF instead of clamping — never hand it an out-of-range value
        views.setInt(R.id.widget_bg_fill, "setImageAlpha", (opacityPct * 255 / 100).coerceIn(0, 255))
        views.setInt(R.id.widget_bg_border, "setColorFilter", palette.border)

        views.setTextViewText(R.id.widget_emoji, content.emoji ?: "")
        views.setViewVisibility(
            R.id.widget_emoji,
            if (content.emoji != null) View.VISIBLE else View.GONE
        )
        views.setTextColor(R.id.widget_refresh, palette.plain)
        views.setContentDescription(
            R.id.widget_refresh,
            context.getString(R.string.cd_widget_refresh)
        )

        if (tier == WidgetTier.SMALL) {
            views.setTextViewText(R.id.widget_temp, content.smallTemp.spannable(palette))
            views.setTextViewText(R.id.widget_location, content.smallLocation.spannable(palette))
        } else {
            views.setTextViewText(R.id.widget_title, content.headerTitle)
            views.setTextColor(R.id.widget_title, palette.title)
            views.setInt(R.id.widget_divider, "setBackgroundColor", palette.divider)
            views.setInt(R.id.widget_guide, "setBackgroundColor", palette.divider)
            views.setTextViewText(R.id.widget_prompt, content.promptLine.spannable(palette))
            views.setTextColor(R.id.widget_cursor, palette.key)

            val slots = LineIds.take(lineCount(tier))
            val lastVisible = minOf(content.bodyLines.size, slots.size) - 1
            slots.forEachIndexed { index, id ->
                val line = content.bodyLines.getOrNull(index)
                if (line != null) {
                    views.setTextViewText(id, line.spannable(palette))
                    views.setViewVisibility(id, View.VISIBLE)
                } else {
                    views.setViewVisibility(id, View.GONE)
                }
                // The ↻/cursor overlay is anchored bottom-right, so only the bottom
                // line has to make room for it; the others run the full width. Set
                // per line rather than on the column, or every line pays for it.
                views.setViewLayoutMargin(
                    id,
                    RemoteViews.MARGIN_END,
                    if (index == lastVisible) RefreshGutterDp else 0f,
                    TypedValue.COMPLEX_UNIT_DIP
                )
            }
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
        return views
    }

    internal fun layoutFor(tier: WidgetTier): Int = when (tier) {
        WidgetTier.SMALL -> R.layout.widget_tweather_small
        WidgetTier.MEDIUM -> R.layout.widget_tweather_medium
        // EXTENDED is MEDIUM plus one line: the large layout already has the slots
        WidgetTier.EXTENDED, WidgetTier.LARGE -> R.layout.widget_tweather_large
    }

    private fun TerminalLine.spannable(palette: WidgetPalette): CharSequence =
        SpannableStringBuilder().apply {
            tokens.forEach { token ->
                val start = length
                append(token.text)
                setSpan(
                    ForegroundColorSpan(palette.colorFor(token.role)),
                    start,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                // SINGLE_TOP is what makes CLEAR_TOP resume the running activity: without
                // it a launchMode=standard MainActivity is finished and rebuilt, replaying
                // the splash and dropping the user back on Explorer.
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                ),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun refreshIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, TweatherWidgetProvider::class.java)
                .setAction(TweatherWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
