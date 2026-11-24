# AV1 Mix — multi-source Android client

This branch is the active product rebuild. The earlier scraper prototype is not part of the production path.

## Implemented in this branch
- Arabic RTL Compose application shell with home, search, player, settings and local watch history.
- Provider-neutral catalog contracts and an admission registry with capability checks, failure isolation and cancellation-safe coroutine handling.
- AdultEmbed v1 adapter using documented JSON search, related and availability endpoints.
- Official AdultEmbed player URLs only; the app does not extract or download media files.
- Local watch history stored on-device and clearable by the user.
- Fixture tests for provider admission, API mapping, URL validation and status checks.
- Legacy site scraper, DOM scanner and guessed stream resolver removed from the active app source.

## Source and playback capability
The first connected catalog is AdultEmbed. Its public v1 documentation explicitly describes app integrations and its official embedded player. The app preserves that player and its ads.

This source currently provides:
- Search and related recommendations.
- Arabic metadata where available.
- A hosted official player.
- Availability checks.

This source does **not** provide app-level AV1 selection, Media3 playback, precise buffer control, or offline downloads. The UI says so rather than pretending those features work. A future provider can be added only when its documented contract supports the needed capability.

The provider's public claims about content approval, rights and 18+ provenance are not an independent audit. See [PROVIDER_REVIEW.md](PROVIDER_REVIEW.md).

## Build and verification
The project targets Android ARM64, JDK 17 and Android SDK 36. GitHub Actions runs unit tests, assembles a debug APK, verifies the signature and checks the bundled AV1 decoder libraries. A successful CI build is not a substitute for playback testing on the target OPPO device.

## Security boundaries
- No HTML scraping, hidden/private endpoint calls, guessed media URLs, DRM bypass or access-control circumvention.
- No download or re-hosting unless a provider expressly permits it.
- Official embeds are not modified and provider advertising is not removed.
- Watch history is local to the device.
