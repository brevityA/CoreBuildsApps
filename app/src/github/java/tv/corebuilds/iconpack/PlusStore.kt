package tv.corebuilds.iconpack

import android.app.Activity
import android.content.Context

/**
 * Core Builds Plus outside Google Play: there is none. The GitHub builds ship
 * every icon and the main wallpaper collection, and the Support screen shows
 * the tip codes; Plus and its extras are a Google Play purchase. The play
 * build type compiles src/play/java/.../PlusStore.kt in this one's place.
 */
object PlusStore {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    fun purchaseToken(context: Context): String? = null

    @Suppress("UNUSED_PARAMETER")
    fun sync(context: Context) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun session(activity: Activity, onState: (PlusState) -> Unit): PlusSession? = null
}
