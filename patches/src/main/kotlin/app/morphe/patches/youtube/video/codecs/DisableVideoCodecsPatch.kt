/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.patches.youtube.video.codecs

import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.matchAllMethodIndicesForEach
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/DisableVideoCodecsPatch;"

val disableVideoCodecsPatch = patch(
    name = "HDR video controls",
    description = "Adds options to disable or force HDR. Codec selection is handled by Ibrahim Universal Video Control.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        HDRCapabilityFingerprint.matchAllMethodIndicesForEach { index ->
            val instruction = getInstruction<FiveRegisterInstruction>(index)
            val register = instruction.registerC

            replaceInstruction(
                index,
                "invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->" +
                        $$"overrideSupportedHdrTypes(Landroid/view/Display$HdrCapabilities;)[I"
            )
        }
    }
}
