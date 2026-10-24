package tv.corebuilds.eq.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent

/**
 * Opens a file for an import, on TVs that ship without the system picker too.
 *
 * Many Android TV, Google TV and Fire TV builds have no DocumentsUI, so
 * `ACTION_OPEN_DOCUMENT` has nothing to resolve to. `ACTION_GET_CONTENT` is
 * then handled by whatever file manager the user installed, and the URI it
 * returns is readable through the ContentResolver for this result, which is
 * all an import needs. Only when neither resolves does the caller say so.
 */
object FilePicker {
    /** Types a text import accepts; CSV is often labelled as a spreadsheet. */
    val TEXT_TYPES = arrayOf("text/*", "text/csv", "application/csv", "application/vnd.ms-excel")

    /**
     * Starts the first picker that resolves, for [requestCode]. Returns false when this
     * device has none. [mimeTypes] narrows what is offered; null offers any file.
     */
    fun open(activity: Activity, requestCode: Int, mimeTypes: Array<String>? = null): Boolean {
        for (action in listOf(Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT)) {
            val intent = Intent(action)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
            if (mimeTypes != null) intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            try {
                activity.startActivityForResult(intent, requestCode)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try the next action.
            }
        }
        return false
    }
}
