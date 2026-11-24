# Provider candidate review — 2026-11-24

This is a capability and rights review, not a recommendation to publish any provider content.

| Candidate | What public evidence establishes | Playback boundary | Decision |
|---|---|---|---|
| AdultEmbed | Public API docs describe search, related, taxonomy, Arabic metadata localization, availability/removal checks, changelog sync and an iframe player. | The documented player is hosted by AdultEmbed and shows its own ads. The API browse response does not provide a direct video URL. | Technical candidate for metadata and official embed only. Disabled pending written confirmation of rights, mobile-app use, and moderation/takedown guarantees. |
| Stash-Box / StashDB | Open-source metadata/indexing API; the self-hosted server starts empty and community metadata is separately maintained. | Metadata/indexing only; not a video stream host. | Potential optional metadata enrichment/deduplication layer. Not a content source. |
| Unofficial multi-provider scrapers | Community repositories claim to parse multiple sites and expose media URLs. | Usually scraping/undocumented endpoints; stability and permissions are not established. | Rejected for production architecture. No hidden endpoint extraction or access-control circumvention. |
| Direct site home-page scraping in legacy prototype | Existing prototype uses Jsoup/WebView DOM parsing and guesses likely video URLs. | Breaks when page markup changes; cannot prove source authorization or actual codec. | Legacy only; do not extend. |

## AdultEmbed verification details
Public docs currently claim:
- Search, related, availability/status, removed-video, trending, taxonomy, and changelog endpoints.
- Arabic is listed as a supported metadata language.
- The hosted player is an iframe at adultembed.com/player/embed/{id}/.
- The player displays AdultEmbed's own ads.
- Browse metadata does not expose the actual media URL.
- The docs describe approved/playable catalog entries and a copyright-removal contact process.

Still unverified:
- Whether its API and player terms expressly authorize a third-party Android app (rather than a conventional website).
- Whether it has documented rights/authorization from every content owner or a sufficiently reliable licensing basis.
- Whether its moderation process can guarantee adult-only, consensual, legally sourced content.
- Whether its player can be used reliably in Android WebView on the target device and whether it permits full-screen, PiP, background, or app-level controls.
- What user identifiers, cookies, referrer data, analytics, and ad SDKs the player uses.

## Required provider questionnaire
Before enabling AdultEmbed, obtain written answers to:
1. May a private Android app display the API metadata and official iframe player?
2. What contractual permission covers the underlying videos and thumbnails, and what territories/uses are covered?
3. What age-verification, performer-age, consent, and moderation checks are applied?
4. How quickly are reported/removed items removed from search, related feeds, caches, and embeds?
5. What analytics, cookies, identifiers, referrer data, and ad technology does the player use?
6. Are WebView embedding, fullscreen, picture-in-picture, and external playback supported?
7. Are there restrictions on local watch history, caching metadata, or app distribution?

No provider is enabled until answers are reviewed. A public endpoint, an HTTP 200 response, or a functioning iframe is not a rights grant.
