# Multi-source Android video client — foundation branch

This branch begins the product rebuild. The earlier AV1 Mix prototype is retained as historical code, not treated as a production-ready app.

## Current state
- Product/source-admission architecture documented in [PRODUCT_FOUNDATION.md](PRODUCT_FOUNDATION.md).
- Provider capability and rights-gate contracts added in the app data layer.
- No external provider is approved or enabled yet.
- No provider is permitted to bypass DRM, paywalls, authentication, geo/access controls, or use undocumented scraping.
- The old site-page scraper and stream guessing code are legacy prototype code and must not be used as the production provider layer.

## Why the rebuild is necessary
The previous prototype attempted to parse pages from multiple sites and infer stream URLs. That approach is fragile and cannot establish playback rights or actual codec availability. The new design uses documented provider interfaces and explicit playback capabilities.

## Playback strategy
- Official embed: provider-hosted player in a restricted WebView, only after mobile embed permission is confirmed.
- Authorized direct stream: Media3/ExoPlayer.
- External-only: open the provider's page.
- Unavailable: no play action.
- AV1 is selected only when the source offers it; the actual codec and decoder must be verified at runtime.
- Offline downloads remain disabled unless expressly allowed by the provider.

## Build
Open this directory in Android Studio with JDK 17+ and Android SDK 36, then run `:app:assembleDebug`.

The existing GitHub Actions workflow is configured to build this branch and check the APK's bundled AV1 libraries. A successful build does not establish that playback works on the target OPPO device.

## Next milestone
Implement and test the provider-neutral UI shell and adapter contract suite. A provider remains disabled until its terms, content provenance/moderation process, takedown behavior, and Android playback permission are confirmed.
