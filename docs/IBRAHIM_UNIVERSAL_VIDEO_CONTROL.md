# Ibrahim Universal Video Control

## Project baseline

- Upstream: `Imimkoo/morphe-patches`
- Working branch: `ibrahim/universal-video-control`
- Base branch: `dev`
- Target YouTube APK: `21.30.216`
- This branch starts from the upstream source; previous experimental AV1 patches and userscripts are not implementation dependencies.

## Product requirement

Add one compact player action named **إبراهيم** beside the existing YouTube video action buttons. Tapping it opens a native in-app popup/bottom sheet. Do not use a floating overlay or a browser userscript.

The panel must expose real controls and live diagnostics for the currently playing video.

## Controls

### Codec policy
1. **Prefer AV1** (default): prefer AV1 when the selected stream and device support it; otherwise allow YouTube's normal fallback.
2. **Force AV1 only**: restrict selection to AV1 and clearly report when no compatible AV1 stream is available. Do not silently fall back in this mode.
3. **Default**: leave YouTube's normal codec selection unchanged.

The selected codec policy must be applied at stream selection/request construction time, before playback starts. Merely changing a displayed preference or device capability flag is not sufficient. The panel must distinguish requested codec from the codec actually selected/decoded.

### Buffer policy
- Enable/disable custom buffer control.
- Target buffered-ahead duration: default 3 seconds.
- Resume threshold: default 2 seconds.
- Editable values with validation and safe bounds.
- Display current forward buffer and whether throttling is active.

Implementation must hook the actual Android YouTube playback pipeline used by this supported version. The browser `MediaSource/SourceBuffer` userscript algorithm is a behavioral reference only and must not be copied into Android bytecode as if the app used browser MSE.

## Live information

Show values only when they can be measured or reliably obtained; otherwise show “غير متاح”.

- Requested codec policy.
- Selected video codec and audio codec, including codec/profile when exposed.
- Video dimensions and frame rate.
- Playback duration and current position.
- Buffered-ahead seconds and buffer state.
- Video/player identity and stream format identifiers when available.
- Bytes received/downloaded and total size only when the source reports reliable values.
- Playback dropped frames / rendered frames when exposed.
- Queue, pending append, and error counters only if the actual playback implementation exposes corresponding concepts.
- Current source/stream metadata. Do not expose signed URLs, tokens, cookies, or other secrets in user-facing diagnostics.

## UI and persistence

- Label: **إبراهيم**.
- Placement: native video action row, next to Like/Share actions.
- Popup must work in portrait and landscape and follow the app theme.
- Settings persist across app restarts.
- Changes that require a new stream take effect on the next stream selection/reload; state this in the UI.
- Avoid claiming that a codec is active until the selected stream/player confirms it.

## Engineering and verification gates

1. Inspect the exact upstream source and patch conventions for YouTube 21.30.216.
2. Identify stream selection, player diagnostics, buffer management, and player action-row insertion hooks.
3. Confirm each hook is present in the target version before writing bytecode patches.
4. Implement settings, native panel, codec policy, and buffer policy as separate testable pieces.
5. Add unit/integration tests for preference, strict AV1-only behavior, no-AV1 behavior, buffer thresholds, and lifecycle/recycled player views.
6. Build the patches artifact and APK with the documented upstream build toolchain.
7. Verify installability and runtime behavior on the target APK. A successful build alone is not proof of working playback controls.

## Important technical constraints

- AV1 cannot be guaranteed for every video: the server may not offer an AV1 representation, and device decoder support/performance varies.
- “Prefer AV1” and “AV1 only” are distinct policies and must remain separate.
- The browser userscript's `getVideoPlaybackQuality`, Resource Timing, `MediaSource`, and `SourceBuffer` data are not automatically available to the native Android player.
- Never display invented file sizes, codec names, FPS, queue counts, or buffer metrics.
- Preserve upstream notices and licensing; use distinct Ibrahim branding for derivative work.

## Current status

This file records requirements and implementation gates only. It is not evidence that the UI, hooks, codec selection, buffer throttling, tests, patches artifact, or APK have been implemented.
