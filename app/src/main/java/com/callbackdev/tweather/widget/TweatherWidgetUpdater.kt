package com.callbackdev.tweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.callbackdev.tweather.data.ActiveSource
import com.callbackdev.tweather.data.LocationSettings
import com.callbackdev.tweather.data.ServiceLocator
import com.callbackdev.tweather.domain.model.City
import com.callbackdev.tweather.domain.model.GpsCityId
import com.callbackdev.tweather.ui.weather.WeatherTranslations
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * Re-renders every widget instance from persisted state (settings + the instance's
 * city + that city's latest Room history commit). Called on every history commit
 * (the repository hook), on provider onUpdate/resize, after configuration, and on
 * foreground-only changes (theme/units/opacity/active city) from MainActivity.
 * No-op with zero widgets.
 */
object TweatherWidgetUpdater {

    suspend fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TweatherWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val settings = ServiceLocator.settingsStore(context).settings.first()
        val cityStore = ServiceLocator.cityStore(context)
        val pinned = ServiceLocator.widgetCityStore(context).current()
        val activeSource = cityStore.activeSource.first()
        val saved = cityStore.cities.first()
        val location = cityStore.locationSettings.first()

        val palette = widgetPalette(settings.themeProfileName)
        val translate = WeatherTranslations.translator(context.resources)
        val now = Instant.now()

        // Instances usually share one city; render once per distinct city and push
        // the same RemoteViews to the whole group.
        ids.groupBy { resolveCity(pinned[it], activeSource, saved, location) }
            .forEach { (city, groupIds) ->
                val entry = city
                    ?.let { ServiceLocator.weatherRepository(context).historyFor(it, limit = 1) }
                    ?.firstOrNull()
                val snapshot = entry?.let {
                    runCatching {
                        Json.decodeFromString<Map<String, String>>(it.snapshotJson)
                    }.getOrNull()
                }
                val content = { tier: WidgetTier ->
                    WidgetContentBuilder.build(
                        snapshot = snapshot,
                        timestampEpochSeconds = entry?.timestampEpochSeconds,
                        temperature = settings.units.temperature,
                        windSpeed = settings.units.windSpeed,
                        tier = tier,
                        translate = translate,
                        updateFrequencyMin = settings.updateFrequencyMin,
                        now = now
                    )
                }
                manager.updateAppWidget(
                    groupIds.toIntArray(),
                    WidgetRenderer.sizeMap(
                        context, content, palette, settings.widgetOpacityPct,
                        cityKey = city?.cacheKey
                    )
                )
            }
    }

    /**
     * The distinct cities placed widgets show but nothing else fetches — i.e. the
     * pinned ones. The sync worker needs them: a pinned instance is by definition not
     * the app's active source, so without its own fetch its snapshot would only ever
     * age, the stale marker would latch on, and the ↻ it offers could never clear it.
     * Empty with no widgets or no pins.
     */
    suspend fun pinnedCities(context: Context): List<City> {
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, TweatherWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return emptyList()
        // Placed instances only: a pin that outlived a missed onDeleted costs no fetch
        val pinned = ServiceLocator.widgetCityStore(context).current()
        val pins = ids.toList().mapNotNull { pinned[it] }.distinct()
        if (pins.isEmpty()) return emptyList()

        val cityStore = ServiceLocator.cityStore(context)
        val activeSource = cityStore.activeSource.first()
        val saved = cityStore.cities.first()
        val location = cityStore.locationSettings.first()
        val activeKey = activeSource.city(location)?.cacheKey
        return pins
            .mapNotNull { resolveCity(it, activeSource, saved, location) }
            .distinctBy { it.cacheKey }
            .filterNot { it.cacheKey == activeKey } // the worker already fetches that one
    }

    /**
     * An unpinned widget follows the app; a pinned one keeps its city even while the
     * user browses another. A pin that can no longer be honoured — a city the user has
     * since removed, or the GPS source after `use_gps` went off — falls back to the
     * active source rather than showing a position the app no longer tracks.
     */
    private fun resolveCity(
        pinnedCityId: Long?,
        activeSource: ActiveSource,
        saved: List<City>,
        location: LocationSettings
    ): City? = when {
        pinnedCityId == null -> activeSource.city(location)
        // not `?: active`: a null fix with the toggle ON is the legitimate "no fix yet"
        pinnedCityId == GpsCityId ->
            if (location.useGps) location.gpsCity else activeSource.city(location)
        else -> saved.firstOrNull { it.id == pinnedCityId } ?: activeSource.city(location)
    }

    // Same rule as the sync worker: the last persisted fix only, never a new one
    private fun ActiveSource.city(location: LocationSettings): City? = when (this) {
        is ActiveSource.Saved -> city
        is ActiveSource.Gps -> lastFix ?: location.gpsCity
    }
}
