/** The Android shell's JavascriptInterface, when there is one. */
export function nativeBridge() {
  return globalThis.CoreLineNative || null;
}
