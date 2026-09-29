/**
 * Overscan calibration.
 *
 * Classic TV panels crop two to five percent of the frame, and the amount
 * differs by set — a layout tuned on one television has its edges cut off on
 * another. Rather than pick a number and hope, show a frame with corner marks
 * and let the viewer walk the margin out until all four are visible.
 *
 * This is the kind of thing a living-room app has to do and a phone app never
 * thinks about, which is most of the difference between the two.
 */

import { $, setHidden, setText, restoreFocus } from '../core/dom.js';
import { nudgeOverscan, currentOverscan } from './settings.js';

let returnFocus = null;

export function openCalibrate() {
  returnFocus = document.activeElement;
  setText('calibrateValue', `${currentOverscan()} px`);
  setHidden('calibrate', false);
  $('calibrate')?.querySelector('[data-action="calibrate-done"]')?.focus();
}

export function closeCalibrate() {
  setHidden('calibrate', true);
  restoreFocus(returnFocus);
  returnFocus = null;
}

export function isCalibrating() {
  return !$('calibrate')?.hidden;
}
