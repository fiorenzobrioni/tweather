package com.callbackdev.tweather.data

import android.content.Context
import android.os.PowerManager

/**
 * Whether the device is in the system's battery saver mode.
 *
 * Read as a function rather than a value: the reader can flip the switch (or the
 * automatic threshold can trip) while the process is alive, and the answer has to be
 * the one that holds at the moment it is asked.
 *
 * The one caller is the editor's silent re-read on resume
 * ([com.callbackdev.tweather.ui.weather.WeatherViewModel.onResumed]) — a convenience
 * fetch nobody asked for out loud, which is exactly the kind of work battery saver
 * exists to postpone. Deliberately NOT consulted by the FAB (an explicit request is
 * never quietly ignored) nor by `weather-sync` (the OS already defers that job under
 * Doze and App Standby, and suppressing it here would silence a severe-weather alert
 * precisely on the phone that has the least charge left to spare).
 */
fun interface PowerSaveState {
    fun isOn(): Boolean

    companion object {
        fun of(context: Context): PowerSaveState {
            val manager = context.getSystemService(PowerManager::class.java)
            return PowerSaveState { manager?.isPowerSaveMode == true }
        }

        /** For tests and previews: never saving. */
        val Off = PowerSaveState { false }
    }
}
