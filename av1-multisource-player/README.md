# AV1 Mix — multi-source Android client

This branch is the active product rebuild. The earlier scraper prototype is not part of the production path.

## Implemented in this branch
- Arabic RTL Compose app with a personalized discovery feed, search, player, settings and local watch history.
- Home discovery merges popular, newest and longest feeds before ranking them against local preferences.
- The player loads the provider-returned URL inside the responsive iframe integration documented by the provider, with WebView cookies, media permissions, load/error state, retry and browser fallback.
- Recommendation weights learn from explicit likes/dislikes and a deliberately weak screen-dwell signal; iframe playback does not expose true watch progress.
- Provider-neutral catalog contracts and an admission registry with capability checks, failure isolation and cancellation-safe coroutine handling.
- AdultEmbed v1 adapter using documented JSON search, related and availability endpoints.
- Provider-returned official player URLs only (allowlisted HTTPS player hosts); the app does not extract or download media files.
- Local watch history stored on-device and clearable by the user.
- Fixture tests for provider admission, API mapping, URL validation and status checks.
- Legacy site scraper, DOM scanner and guessed stream resolver removed from the active app source.

## Source and playback capability
The first connected catalog is AdultEmbed. Its public v1 documentation explicitly describes app integrations and its official embedded player. The app preserves that player and its ads. The API response currently returns a player URL on `pornalizer.app` for the live test record, while the docs show `adultembed.com`; both exact HTTPS player hosts are allowlisted, and CI checks the URL returned by the API rather than inventing a replacement.

This source currently provides:
- Search and related recommendations.
- Arabic metadata where available.
- A hosted official player.
- Availability checks.

This source does **not** provide app-level AV1 selection, Media3 playback, precise buffer control, or offline downloads. The UI says so rather than pretending those features work. A future provider can be added only when its documented contract supports the needed capability.

The provider's public claims about content approval, rights and 18+ provenance are not an independent audit. See [PROVIDER_REVIEW.md](PROVIDER_REVIEW.md).

## Build and verification
The project targets Android ARM64, JDK 17 and Android SDK 36. GitHub Actions runs unit tests, assembles a debug APK, verifies the signature and checks the bundled AV1 decoder libraries. CI now checks the live search, availability endpoint and actual returned player page response, in addition to unit tests, APK assembly and AV1 library verification. A successful HTTP/player-page check is not a substitute for an actual video playback test on the target OPPO device.

## Security boundaries
- No HTML scraping, hidden/private endpoint calls, guessed media URLs, DRM bypass or access-control circumvention.
- No download or re-hosting unless a provider expressly permits it.
- Official embeds are not modified and provider advertising is not removed.
- Watch history is local to the device.
