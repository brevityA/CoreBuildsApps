package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable

/**
 * What made Projectivy ask for wallpapers, as its provider API (version 1)
 * sends it.
 *
 * Projectivy writes the event's type first, then that event's own fields
 * (now-playing title, focused card colour and so on). The walls here do not
 * depend on any of them, so only the type is read; the event is the call's
 * only argument, so nothing after it is left unread out of place. Core
 * Motion's copy (motion-plugin/) is the full sealed class.
 */
class Event(val eventType: Int) : Parcelable {

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(eventType)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Event> {
        const val TIME_ELAPSED = 1
        const val NOW_PLAYING_CHANGED = 2
        const val CARD_FOCUSED = 4
        const val PROGRAM_CARD_FOCUSED = 8
        const val LAUNCHER_IDLE_MODE_CHANGED = 16

        override fun createFromParcel(parcel: Parcel): Event = Event(parcel.readInt())

        override fun newArray(size: Int): Array<Event?> = arrayOfNulls(size)
    }
}
