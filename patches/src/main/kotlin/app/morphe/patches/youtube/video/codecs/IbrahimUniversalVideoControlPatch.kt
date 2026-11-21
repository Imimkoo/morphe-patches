/*
 * Copyright 2026.
 * Ibrahim Universal Video Control - independent patch implementation.
 */
package app.morphe.patches.youtube.video.codecs

import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.patch
import app.morphe.patches.youtube.layout.player.buttons.addPlayerBottomButton
import app.morphe.patches.youtube.layout.player.buttons.playerOverlayButtonsHookPatch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val IBRAHIM_CLASS =
    "Lapp/morphe/extension/youtube/videoplayer/IbrahimVideoControlButton;"

/**
 * This patch is intentionally independent of DisableVideoCodecsPatch and PlaybackBufferPatch.
 * Select this patch instead of Disable video codecs when using Ibrahim's codec controls.
 */
val ibrahimUniversalVideoControlPatch = patch(
    name = "Ibrahim: Universal video control",
    description = "Adds an independent player control for Default, Prefer AV1, and Force AV1-only policies."
) {
    dependsOn(
        sharedExtensionPatch,
        playerOverlayButtonsHookPatch
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
    }
}
