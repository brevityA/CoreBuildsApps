package tv.corebuilds.eq.ui

import android.view.KeyEvent
import android.view.View
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

/**
 * A row with levels rather than on/off: the same look as [showSwitch]
 * (lit while not at its lowest level), the value in words, and a spoken label
 * that says the value and how to change it.
 */
fun Button.showLevel(text: CharSequence, raised: Boolean, spoken: CharSequence) {
    this.text = text
    isActivated = raised
    contentDescription = spoken
}

/**
 * Left and right step a level row down and up. At either end the key
 * is not consumed, so Left still reaches the rail and Right the detail panel:
 * the row never traps focus.
 */
fun View.stepWithLeftRight(canDown: () -> Boolean, down: () -> Unit, canUp: () -> Boolean, up: () -> Unit) {
    setOnKeyListener { _, keyCode, event ->
        if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (canDown()) { down(); true } else false
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (canUp()) { up(); true } else false
            else -> false
        }
    }
}
