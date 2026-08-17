# How we compare to Projectivy Icon Pack

Decompiled **Projectivy Icon Pack 1.1.9** (17.1 MB) and compared it against
ours mechanism by mechanism. The question was whether we are *as solid* on
auto-apply, discovery, and TV banners — not whether we have as many icons.

## Mechanics — we match or exceed

| Capability | Theirs | Ours |
| --- | --- | --- |
| `android:banner` (TV home row) | ✅ | ✅ |
| `LEANBACK_LAUNCHER` category | ✅ | ✅ |
| `android.software.leanback` feature | ✅ | ✅ |
| `touchscreen` not required | ✅ | ✅ |
| Projectivy `ACTION_PICK_ICON` discovery | ✅ | ✅ |
| Launcher theme actions (Nova/ADW/Apex/…) | ✅ | ✅ |
| Direct apply intent | ✅ (Blueprint) | ✅ (`ApplyIconPack.kt`) |
| `<queries>` for Android 11+ visibility | ❌ **absent** | ✅ |
| Adaptive icon (`mipmap-anydpi-v26`) | ✅ | ✅ |
| Round icon | ✅ | ✅ |

**We are ahead on one real thing.** Their manifest has no `<queries>` block.
They get away with it because Blueprint targets an older SDK level; at
`targetSdk 34` its absence silently breaks every launcher lookup. Ours declares
it explicitly.

## The one place they were more robust — now fixed

Their real appfilter (`res/hA.xml`, 190 KB) carries **956 components, 955 of
them fully-qualified**:

```
air.RTE.OSMF.Minimal/com.finconsgroup.droid.activities.MainActivity
```

Ours was the inverse — **90 of 117 used the shorthand form**:

```
au.com.seven.inferno/.MainActivity
```

Launchers match the **literal string** inside `ComponentInfo{...}`. Not all of
them expand a leading dot, so a shorthand-only entry can silently fail to apply
while looking perfectly correct in the catalog. This is the same class of bug
as the missing discovery action: no error, just an icon that never appears.

**Fixed.** The generator now emits both forms for every component — 117 catalog
entries become **205 appfilter entries** — and the validator fails by name when
a twin is missing. Verified the guard bites by deleting one and watching it
fail.

## Coverage — the real difference

| | Theirs | Ours |
| --- | --- | --- |
| Icons | ~1,480 drawables | 68 |
| Components | 956 | 205 (117 catalog) |
| Unique packages | 887 | 98 |
| APK size | 17.1 MB | ~3 MB |

This is the honest gap, and it is a *content* gap, not an engineering one. Their
list accumulated over years of user requests.

Worth noting on quality: their components are broad but not device-verified for
any particular TV. Ours are 100% read off real hardware — which is why a scan of
your device found **8 of our own mappings wrong** before v1.1.0. Breadth and
accuracy are different axes.

## Why their APK is 5× larger

They bundle the full Blueprint framework: wallpapers, Muzei integration, Play
Billing, Firebase, piracy checker, OkHttp, Play Services (base, location,
places). Visible in their asset list — `billing.properties`,
`firebase-encoders.properties`, `play-services-*.properties`.

We ship icons, an apply contract, and a browser grid. Nothing in that stack is
required to be a well-behaved icon pack, and every dependency is a permission
prompt or a CVE we don't inherit.

Their `assets/` also still contains unmodified Blueprint boilerplate — an
`appfilter.xml` with 11 stub `com.android.contacts` entries that does nothing,
alongside `desk.xml`, `themecfg.xml`, and a theme font for launchers they don't
support. Harmless, but dead weight.

## Verdict

On **mechanics** we are equal or better — and we hold a genuine advantage on
`<queries>`. The shorthand-component weakness was real and is now closed with a
validator guard so it cannot regress.

What remains is **coverage**, which is a matter of adding catalogue entries in
usage-ordered waves, not of changing how the pack works.
