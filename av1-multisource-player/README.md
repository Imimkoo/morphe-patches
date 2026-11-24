# AV1 Mix — Android prototype

A personal multi-source video feed prototype for Android. It has two feeds (short/vertical and long/horizontal), a provider adapter layer, one Media3 player, and a software AV1 path using NextLib's dav1d-backed FFmpeg renderer.

## Initial sources

| Source | Feed | AV1 status from research |
|---|---|---|
| XFree | Short / vertical | Not verified; inspect actual stream at runtime |
| Eporner | Long | AV1 variants documented for selected videos |
| XHamster | Long | AV1 variants reported, but current extraction is unstable |

The parser is conservative: it creates cards only when a likely video-page link and thumbnail are found. It currently handles public progressive MP4 and HLS/DASH URLs exposed in page HTML. If extraction fails, it falls back to the original site in a WebView. It does not bypass DRM, paywalls, authentication, or access controls.

## AV1 playback

- Media3 1.11.1 handles playback.
- NextLib 1.11.1-0.19.0 provides FFmpeg software renderers with dav1d AV1 decoding.
- Extension renderers are enabled after Android platform renderers. Platform decoding remains preferred; dav1d is available as a fallback.
- The player debug panel is intended to report the input format and actual decoder. A bundled library or an AV1 label in a page is not proof of AV1 playback.

## Build

Open this directory in Android Studio with JDK 17+ and Android SDK 36, then run `:app:assembleDebug`. GitHub Actions builds the debug APK and uploads it as an artifact.

## Prototype limits

- Site layouts change; site-specific selectors/API adapters will be needed after live-device tests.
- This first milestone has a mixed-source feed and unified player. Auto-play swipe pager and persistent recommendations are next milestones.
- XFree's actual stream codec is not confirmed.
- No playback or extraction has yet been tested on the OPPO device. Build success is not runtime success.

## License

NextLib is GPL-3.0. Any distributed build containing it must comply with that license and preserve relevant notices/source availability.
