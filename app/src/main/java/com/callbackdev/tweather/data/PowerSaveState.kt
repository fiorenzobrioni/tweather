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
 * Its callers are the automatic re-reads a screen makes on its own, the one on coming
 * back to the foreground first of all: conveniences nobody asked for out loud, which
 * is exactly the kind of work battery saver exists to postpone. Deliberately NOT
 * consulted by an explicit refresh (a request made out loud is never quietly
 * ignored), by a screen that has nothing to show yet (postponing there leaves it
 * empty, and an empty screen is not a cheaper one), nor by the periodic sync job: the
 * OS already defers that under Doze and App Standby, and suppressing it here would
 * silence a severe-weather alert precisely on the phone with the least charge left to
 * spare.
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
