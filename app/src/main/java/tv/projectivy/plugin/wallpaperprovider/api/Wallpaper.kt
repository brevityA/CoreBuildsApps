package tv.projectivy.plugin.wallpaperprovider.api

import android.os.Parcel
import android.os.Parcelable

/**
 * One wallpaper handed to Projectivy, as its wallpaper-provider API (version
 * 1) defines it.
 *
 * Core Motion (motion-plugin/) carries Projectivy's own copy, which is written
 * with Parcelize. This app does not use that compiler plugin, so the parcel is
 * written by hand in the same order Parcelize writes it: the constructor's
 * fields, in declaration order, Strings with writeString (null-safe) and Ints
 * with writeInt. Projectivy reads this layout; tests/test_projectivy_walls.py
 * holds the order to Core Motion's declaration.
 */
data class Wallpaper(
    val uri: String,
    val type: Int = WallpaperType.IMAGE,
    val displayMode: Int = WallpaperDisplayMode.CROP,
    val title: String? = null,
    val source: String? = null,
    val author: String? = null,
    val actionUri: String? = null,
) : Parcelable {

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(uri)
        parcel.writeInt(type)
        parcel.writeInt(displayMode)
        parcel.writeString(title)
        parcel.writeString(source)
        parcel.writeString(author)
        parcel.writeString(actionUri)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Wallpaper> {
        override fun createFromParcel(parcel: Parcel): Wallpaper = Wallpaper(
            uri = parcel.readString().orEmpty(),
            type = parcel.readInt(),
            displayMode = parcel.readInt(),
            title = parcel.readString(),
            source = parcel.readString(),
            author = parcel.readString(),
            actionUri = parcel.readString(),
        )

        override fun newArray(size: Int): Array<Wallpaper?> = arrayOfNulls(size)
    }
}

/** Projectivy's wallpaper types; the walls here are all [IMAGE]. */
object WallpaperType {
    const val IMAGE = 0
    const val DRAWABLE = 1
    const val ANIMATED_DRAWABLE = 2
    const val LOTTIE = 3
    const val VIDEO = 4
    const val COLOR = 5
}

/** How Projectivy fits a wallpaper to the screen; the walls are 16:9, so [CROP]. */
object WallpaperDisplayMode {
    const val DEFAULT = 0
    const val CROP = 1
    const val STRETCH = 2
    const val BLUR = 3
    const val FIT_CENTER = 4
    const val FIT_CENTER_BLURRED_BG = 5
}
