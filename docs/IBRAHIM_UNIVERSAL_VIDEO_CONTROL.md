# Ibrahim Universal Video Control

## Project baseline

- Upstream: `Imimkoo/morphe-patches`
- Working branch: `ibrahim/universal-video-control`
- Base branch: `dev`
- Target YouTube APK: `21.30.216`
- This branch starts from the upstream source. Ibrahim controls are a new, independent implementation; existing codec and playback-buffer patches are not dependencies and must not be enabled or reused as the implementation.

## Product requirement

Add one compact player action named **إبراهيم** beside the existing YouTube video action buttons. Tapping it opens a native in-app popup/bottom sheet. Do not use a floating overlay or a browser userscript.

The panel must expose real controls and live diagnostics for the currently playing video.

## Architecture rule

- Build Ibrahim's codec policy, buffer controller, diagnostics collector, settings and player panel as independent modules owned by this project.
- Do not call, wrap, depend on, or silently activate the existing upstream `DisableVideoCodecsPatch` or `PlaybackBufferPatch` as the implementation of Ibrahim controls.
- Inspect upstream code only to understand Android/YouTube APIs, patch injection conventions, and version-specific constraints. New behavior and settings must be implemented under distinct Ibrahim classes and preference keys.
- Keep the modules isolated behind a small coordinator. A failure in optional diagnostics must never interrupt playback. If a hook cannot be verified for YouTube 21.30.216, disable that control and report it honestly rather than applying a speculative bytecode edit.

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

- The main entry point is a single **إبراهيم** action in the video action row directly below the video, alongside Like/Share. It opens one compact scrollable bottom sheet/popup; controls and diagnostics stay inside this panel rather than adding separate player buttons.
- Organize the panel into clear sections: playback/AV1, buffer, live video information, and diagnostics. Keep the most-used controls at the top.

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

Implementation is underway. Source now contains an independent codec policy, a separate load-control hook, and a combined in-player settings dialog. This is still not a fully verified release.

### Implemented in source
- Added `IbrahimVideoControlButton` with its own persistent preference namespace.
- Added a single **إبراهيم** player control and a native dialog containing codec modes, buffer enable/disable, Target and Resume sliders.
- Added a distinct `Ibrahim: Universal video control` patch and a distinct `Ibrahim: Buffer control` patch.
- Codec callbacks are injected at the existing VP9 capability and codec MIME decision points, but call only Ibrahim's implementation.
- Buffer control reads the player's buffered-duration field inside the identified load-control method and applies hysteresis: stop loading at Target, resume only at Resume. It does not call or depend on `PlaybackBufferPatch`.
- Defaults: Target 3 seconds, Resume 2 seconds.
- Modes:
  - Default: leave codec decisions unchanged.
  - Prefer AV1: disables VP9 while leaving other video MIME types available. This is a limited preference and cannot guarantee AV1 wins over AVC.
  - Force AV1 only: rejects non-AV1 video MIME types while preserving audio codec selection.

### Not implemented / not verified
- The dialog is currently a native choice/settings dialog, not the final themed bottom sheet requested.
- Live selected video codec/profile, FPS, exact buffer-ahead, byte counts, frame counters, and diagnostics counters are not yet displayed.
- No runtime patch application or playback verification against the exact YouTube 21.30.216 APK has been performed.
- GitHub Actions is compiling/testing the source and will build the MPP only if the test job succeeds. No final MPP artifact has yet been verified.

A successful source build does not prove that the bytecode hooks apply cleanly to the target APK or that playback behavior is correct. Runtime verification remains a release gate.
