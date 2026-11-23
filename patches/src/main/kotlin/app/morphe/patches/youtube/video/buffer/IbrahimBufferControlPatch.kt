/*
 * Copyright 2026.
 * Ibrahim Universal Video Control - independent buffer implementation.
 */
package app.morphe.patches.youtube.video.buffer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.patch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val IBRAHIM_CONTROL_CLASS =
    "Lapp/morphe/extension/youtube/videoplayer/IbrahimVideoControlButton;"

/**
 * Independent load-control policy. Does not use PlaybackBufferPatch or its scaling logic.
 * The existing fingerprint is used only to locate the player's buffered-duration read.
 */
val ibrahimBufferControlPatch = patch(
    name = "Ibrahim: Buffer control",
    description = "Limits forward buffering with an independent target/resume hysteresis controller."
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        ShouldContinueLoadingFingerprint.let { fingerprint ->
            fingerprint.method.apply {
                findInstructionIndicesReversedOrThrow(
                    fieldAccess(fingerprint.instructionMatches.first().fieldReference)
                ).forEach { bufferedIndex ->
                    val register = getInstruction<TwoRegisterInstruction>(bufferedIndex).registerA
                    addInstructions(
                        bufferedIndex + 1,
                        """
                            invoke-static/range { v$register .. v${register + 1} }, $IBRAHIM_CONTROL_CLASS->applyBufferPolicy(J)J
                            move-result-wide v$register
                        """
                    )
                }
            }
        }
    }
}
