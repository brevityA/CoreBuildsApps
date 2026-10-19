package tv.corebuilds.eq.ui

import android.widget.Button
import androidx.annotation.StringRes

/**
 * Show an on/off row's state (1.3.0): the words ("Night mode: On") and the
 * look ([bg_toggle][tv.corebuilds.eq.R.drawable.bg_toggle] tint and the
 * [ic_switch][tv.corebuilds.eq.R.drawable.ic_switch] knob, both keyed on
 * activated), set together so the two can never disagree.
 */
fun Button.showSwitch(on: Boolean, @StringRes onText: Int, @StringRes offText: Int) {
    setText(if (on) onText else offText)
    isActivated = on
}
