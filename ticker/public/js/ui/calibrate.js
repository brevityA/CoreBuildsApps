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

import { store } from '../core/store.js';
import { $, setHidden, setText } from '../core/dom.js';
import { nudgeOverscan } from './settings.js';

export function openCalibrate() {
  setText('calibrateValue', `${store.state.overscan} px`);
  setHidden('calibrate', false);
  $('calibrate')?.querySelector('[data-action="calibrate-done"]')?.focus();
}

export function closeCalibrate() {
  setHidden('calibrate', true);
}

export function isCalibrating() {
  return !$('calibrate')?.hidden;
}
