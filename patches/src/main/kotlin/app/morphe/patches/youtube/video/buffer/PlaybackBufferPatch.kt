/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for license and attribution details.
 */

package app.morphe.patches.youtube.video.buffer

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.patch
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patches.youtube.video.information.videoInformationPatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val PLAYBACK_BUFFER_CLASS_DESCRIPTOR =
    "Lapp/morphe/extension/youtube/patches/PlaybackBufferPatch;"

/**
 * ExoPlayer adapter for the same target/resume policy used by the SABR adapter.
 * It changes only the buffered-duration decision; it does not alter the player's byte limit
 * or discard media already buffered behind the playback position.
 */
val playbackBufferPatch = patch(
    name = "Ibrahim smart playback buffer",
    description = "Applies the shared target, resume and stability policy to ExoPlayer streams."
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        videoInformationPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        ShouldContinueLoadingFingerprint.let {
            it.method.apply {
                findInstructionIndicesReversedOrThrow(
                    fieldAccess(it.instructionMatches.first().fieldReference)
                ).forEach { bufferedIndex ->
                    val register = getInstruction<TwoRegisterInstruction>(bufferedIndex).registerA

                    addInstructions(
                        bufferedIndex + 1,
                        """
                            invoke-static/range { v$register .. v${register + 1} }, $PLAYBACK_BUFFER_CLASS_DESCRIPTOR->adjustBufferedDurationUs(J)J
                            move-result-wide v$register
                        """
                    )
                }
            }
        }
    }
}
