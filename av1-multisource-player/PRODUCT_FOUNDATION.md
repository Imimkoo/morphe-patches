# Multi-source video app — product foundation

## Product goal
A private Android discovery client that makes independently operated, rights-cleared video catalogs feel like one coherent library. The app does not host, copy, proxy, or re-upload third-party video files.

## Non-negotiable source admission
A provider is disabled until all of the following are documented:
1. Its API/embed terms permit this exact client use.
2. The provider has a credible content moderation and copyright/takedown process.
3. The playback method is explicitly permitted (official embed or authorized stream).
4. Content is restricted to verified adult performers and legal material; no minors, non-consensual material, hidden-camera material, or uncertain provenance.
5. Removal signals can be consumed and acted on promptly.

A public API or a playable URL is not proof of a content license. AdultEmbed is currently a technical candidate only: public docs describe metadata endpoints, related results, status/removal endpoints and an iframe player, but the rights grant and mobile-app embedding terms have not been verified. Do not enable it in production until written terms/permission are confirmed.

## Architecture decisions
- Android native app, Kotlin + Jetpack Compose.
- Provider adapters return normalized metadata and explicit playback capabilities.
- No generic HTML scraping, DOM selectors, hidden API extraction, DRM bypass, paywall bypass, or authentication circumvention.
- Playback is capability-driven:
  - OFFICIAL_EMBED: open the provider's documented player in a restricted WebView.
  - AUTHORIZED_DIRECT_STREAM: Media3/ExoPlayer.
  - EXTERNAL_ONLY: open the original provider page.
  - UNAVAILABLE: do not show a play action.
- Media3 is not assumed to control an embedded provider player.
- AV1 is a playback preference only when the provider actually exposes an AV1 rendition. Actual input codec and decoder must be observed at runtime. A bundled software decoder is a fallback, not a way to force a source to publish AV1.
- Playback cache is bounded and separate from explicit offline downloads. Offline downloads are disabled unless the provider expressly permits them.
- Local-first profile: watch history, feedback and recommendations stay on-device by default.

## Discovery and recommendation
Normalize provider IDs, canonical title, duration, thumbnail, performer/studio identifiers, tags, source, publish/update time, and moderation state.
Deduplicate first by provider canonical ID, then by verified external scene IDs where licensing permits. Do not merge solely on title similarity.
Recommendation ranking uses explicit positive/negative feedback, completion ratio, early skips, repeat watches, and tag/performer co-occurrence. Apply freshness and diversity controls to avoid a repetitive feed. Never infer sensitive traits from viewing behavior.

## User experience
- Home: a single mixed feed, with optional filters rather than a forced short/long split.
- Search: one query across all admitted providers.
- Video detail: source, duration, tags, and related items across providers.
- Player: one consistent shell; provider embeds remain visibly provider-hosted where applicable.
- Feedback: like, not interested, hide provider/tag, and clear history.
- Privacy: local history controls, pause learning, clear all local profile data.
- Accessibility: Arabic-first RTL, readable controls, captions where the source supplies them.

## Playback and network strategy
Do not use a fixed buffer target as a proxy for data savings. Measure actual bytes downloaded, rebuffering, seek latency and memory. For direct streams use Media3's bounded disk cache and adaptive loading; tune only after device/network measurements. Seeking should not intentionally prefetch long future ranges. Embedded players are outside this control boundary.

## Quality gates
- Provider contract tests: schema, pagination, timeouts, retry/backoff, removed-item handling.
- Rights gate tests: provider disabled by default; no playback without approved capability.
- Deduplication tests: same provider ID, aliases, title collision, different cuts.
- Recommendation tests: cold start, sparse history, feedback reversals, diversity.
- Player tests: HLS/DASH/MP4, seek, background/foreground, audio focus, rotation, lifecycle release.
- Codec tests: actual input MIME/codec and decoder name; never infer from URL text or page HTML.
- Performance tests on the target OPPO device: startup, scroll jank, RAM, heat, battery, bytes transferred, rebuffer rate.
- Release gate: build, install, launch, provider contract suite, signed APK, SHA-256, and a device test report. Build success alone is not runtime success.

## Current evidence / open blockers
- AdultEmbed documents search, related videos, Arabic metadata localization, status/removal endpoints and a hosted iframe player. It also states that its player displays its own ads. Its API docs do not establish a clear rights grant for redistribution or this Android client; written confirmation is required.
- Stash-Box is an open-source metadata/indexing service that starts empty unless populated; it is not a ready-made streaming catalog.
- Media3 supports a direct-stream path and disk caching. A hosted iframe player does not become a Media3 stream.
- The previous prototype's general-purpose page scraping and stream URL guessing are not suitable as the product foundation and must not be promoted to production.

## First milestone
Build a clean provider-neutral app shell and a source-admission test harness. Keep every unapproved provider disabled. Add the first live provider only after terms, playback rights, and real-device behavior are verified.
