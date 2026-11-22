/*
 * Copyright 2026.
 * Ibrahim Universal Video Control - independent patch implementation.
 */
package app.morphe.patches.youtube.video.codecs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.patch
import app.morphe.patches.youtube.layout.player.buttons.addPlayerBottomButton
import app.morphe.patches.youtube.layout.player.buttons.playerOverlayButtonsHookPatch
import app.morphe.patches.youtube.video.buffer.ibrahimBufferControlPatch
import app.morphe.patches.youtube.misc.spoof.spoofVideoStreamsPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE

private const val IBRAHIM_CLASS =
    "Lapp/morphe/extension/youtube/videoplayer/IbrahimVideoControlButton;"

/**
 * This patch is intentionally independent of DisableVideoCodecsPatch and PlaybackBufferPatch.
 * Select this patch instead of Disable video codecs when using Ibrahim's codec controls.
 */
val ibrahimUniversalVideoControlPatch = patch(
    name = "Ibrahim: Universal video control",
    description = "Adds Ibrahim controls, AV1-first spoof client selection, and independent buffer policy."
) {
    dependsOn(
        sharedExtensionPatch,
        spoofVideoStreamsPatch,
        playerOverlayButtonsHookPatch,
        ibrahimBufferControlPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        addPlayerBottomButton(IBRAHIM_CLASS)

        // Reuse the verified codec capability decision points, but route them to Ibrahim's
        // independent policy implementation. The existing codec patch is not invoked.
        Vp9CapabilityFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $IBRAHIM_CLASS->allowVP9()Z
                move-result v0
                if-nez v0, :ibrahim_default
                return v0
                :ibrahim_default
                nop
            """
        )

        CodecSupportCheckFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static { p2 }, $IBRAHIM_CLASS->allowCodec(Ljava/lang/String;)Z
                move-result v0
                if-nez v0, :ibrahim_default
                return v0
                :ibrahim_default
                nop
            """
        )

        // Use the APK-bundled dav1d JNI library rather than Android MediaCodec.
        // This removes the hardware/system-codec gate only for AV1 streams.
        EmbeddedDav1dFormatFingerprint.method.addInstructionsWithLabels(
            0,
            """
                if-eqz p1, :ibrahim_no_dav1d
                iget-object v0, p1, Ldefpackage/edc;->q:Ljava/lang/String;
                const-string v1, "video/av01"
                invoke-virtual {v1, v0}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :ibrahim_no_dav1d
                invoke-static {}, Landroidx/media3/decoder/av1/Dav1dLibrary;->a()Z
                move-result v0
                return v0
                :ibrahim_no_dav1d
                const/4 v0, 0x0
                return v0
            """
        )

        // Always consult the embedded dav1d eligibility check, not the rollout gate.
        EmbeddedDav1dSelectionFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-direct {p0, p1}, Ldefpackage/sun;->bw(Ldefpackage/edc;)Z
                move-result v0
                return v0
            """
        )
    }
}
