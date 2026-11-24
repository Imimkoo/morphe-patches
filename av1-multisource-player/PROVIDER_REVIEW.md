# Provider candidate review — 2026-11-24

This review distinguishes provider-published integration permission from independent verification of the underlying content-rights chain.

| Candidate | Public evidence | Playback boundary | Decision |
|---|---|---|---|
| AdultEmbed API v1 | Its current docs explicitly say v1 supports a tube site, app or bot; it exposes search, related, Arabic metadata, status/removal feeds, and a permanent official player URL. It states only approved/playable videos are returned and its originating providers meet 18 U.S.C. 2257 record-keeping requirements. | Official AdultEmbed player only. The API's embed URL is used; no raw media URL is extracted. Player ads and source controls remain intact. | Enabled narrowly as the first catalog provider, relying on the provider's published API/embed representation. No direct streams, downloads, codec selection, or player modification. Rights/provenance claims remain provider assertions, not an independent audit. |
| Stash-Box / StashDB | Open-source metadata/indexing API; self-hosted server starts empty and community metadata is separately maintained. | Metadata/indexing only; not a video stream host. | Optional future metadata enrichment/deduplication layer. Not a playback source. |
| Unofficial multi-provider scrapers | Community repositories claim to parse multiple sites and expose media URLs. | Scraping/undocumented endpoints; stability and permissions are not established. | Rejected for production. No hidden endpoint extraction or access-control circumvention. |
| Direct site home-page scraping in legacy prototype | Previous prototype used Jsoup/WebView DOM parsing and guessed likely video URLs. | Cannot prove source authorization or actual codec; brittle when markup changes. | Removed from the active application path and not to be restored. |

## AdultEmbed integration boundary

The current public v1 documentation establishes:
- It is intended for integrations described as websites, apps and bots.
- Search results include an official `embed.url`; the player URL is hosted at `adultembed.com/player/embed/{id}/`.
- Search supports Arabic metadata and returns a `results` collection.
- Related-video and per-video status endpoints exist.
- Removed videos are exposed through the API; videos that stop being playable are removed from normal endpoints.
- The player displays AdultEmbed's own ads. The app does not hide, replace, or interfere with them.
- The service states its catalog entries are approved/playable and that originating providers meet 18 U.S.C. 2257 record-keeping requirements.

The adapter intentionally:
- Calls only the documented v1 JSON API.
- Accepts playback URLs only when the HTTPS host is `adultembed.com` and the path is the official player path.
- Falls back only to the documented permanent player URL for the returned video ID.
- Never downloads or extracts the underlying media file.
- Does not claim AV1, quality, offline, or buffer control. Those remain under the embedded player's control.
- Keeps watch history on-device.

## Remaining validation

- Runtime API response and Android WebView playback must pass device tests; public documentation alone does not prove runtime compatibility on every device.
- The provider's statements about rights, performer age, and source approval are not an independent audit. Any credible report or removal signal must suppress the item.
- Player cookies, analytics, referrer behavior, and ad technology need privacy inspection.
- Fullscreen, PiP, and background playback are not claimed until verified.

## Rejected implementation patterns

- HTML scraping, hidden/private endpoints, URL guessing, direct media extraction, DRM bypass, ad removal, or player overlays.
- Treating metadata access as permission to download or re-host a video.
- Claiming the embedded player can be forced to AV1 or controlled by Media3 when the provider does not expose that capability.
