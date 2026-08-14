package com.callbackdev.tweather.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.SizeF
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
    private const val MediumLineCount = 4

    // Measured off the layouts (13sp line + 3dp lineSpacingExtra; 20sp temp over an
    // 11sp city in the small tier) — keep in sync if the styles change.
    private const val ChromeHeightDp = 68f
    private const val BodyLineHeightDp = 20f
    private const val SmallMinHeightDp = 48f

    fun sizeMap(
        context: Context,
        content: (WidgetTier) -> WidgetContent,
        palette: WidgetPalette,
        opacityPct: Int
    ): RemoteViews {
        val (small, medium, large) = breakpoints()
        return RemoteViews(
            mapOf(
                small to render(context, content(WidgetTier.SMALL), palette, opacityPct, WidgetTier.SMALL),
                medium to render(context, content(WidgetTier.MEDIUM), palette, opacityPct, WidgetTier.MEDIUM),
                large to render(context, content(WidgetTier.LARGE), palette, opacityPct, WidgetTier.LARGE)
            )
        )
    }

    /**
     * A sizes-map key is a promise that the layout FITS in that many dp — the host
     * clips silently otherwise, so the heights are derived from the layouts instead
     * of guessed: fixed chrome (header 34 + divider 1 + prompt 33) + one body line
     * per slot + the body's bottom padding. Below the smallest key the launcher
     * falls back to it, so a minimum-size widget still gets SMALL.
     */
    internal fun minHeightDp(lines: Int): Float = ChromeHeightDp + lines * BodyLineHeightDp + 12f

    internal fun breakpoints(): List<SizeF> = listOf(
        SizeF(110f, SmallMinHeightDp),
        SizeF(160f, minHeightDp(MediumLineCount)),
        SizeF(160f, minHeightDp(LineIds.size))
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
            views.setTextColor(R.id.widget_menu, palette.comment)
            views.setInt(R.id.widget_divider, "setBackgroundColor", palette.divider)
            views.setInt(R.id.widget_guide, "setBackgroundColor", palette.divider)
            views.setTextViewText(R.id.widget_prompt, content.promptLine.spannable(palette))
            views.setTextColor(R.id.widget_cursor, palette.key)

            val slots = if (tier == WidgetTier.MEDIUM) LineIds.take(MediumLineCount) else LineIds
            slots.forEachIndexed { index, id ->
                val line = content.bodyLines.getOrNull(index)
                if (line != null) {
                    views.setTextViewText(id, line.spannable(palette))
                    views.setViewVisibility(id, View.VISIBLE)
                } else {
                    views.setViewVisibility(id, View.GONE)
                }
            }
        }

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context))
        return views
    }

    internal fun layoutFor(tier: WidgetTier): Int = when (tier) {
        WidgetTier.SMALL -> R.layout.widget_tweather_small
        WidgetTier.MEDIUM -> R.layout.widget_tweather_medium
        WidgetTier.LARGE -> R.layout.widget_tweather_large
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
