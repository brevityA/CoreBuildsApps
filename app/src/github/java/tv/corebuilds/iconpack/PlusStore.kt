package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context

/**
 * Core Builds Plus outside Google Play: nothing to buy. The GitHub builds ship
 * every feature free, and the Support screen shows the tip codes instead. The
 * play build type compiles src/play/java/.../PlusStore.kt in this one's place.
 */
object PlusStore {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun purchaseToken(context: Context): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun session(activity: Activity, onState: (PlusState) -> Unit): PlusSession? = null
}
