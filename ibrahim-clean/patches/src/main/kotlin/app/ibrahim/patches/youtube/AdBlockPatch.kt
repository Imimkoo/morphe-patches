package app.ibrahim.patches.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patches.youtube.shared.Constants
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.patch

private const val EXTENSION_CLASS = "Lapp/ibrahim/youtube/IbrahimAds;"

private val ADD_METHOD_CALL = methodCall(
    opcode = Opcode.INVOKE_VIRTUAL,
    name = "add",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Z",
)

internal object PlayerBytesAdLayoutFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L"),
    strings = listOf(
        "Bootstrapped layout construction resulted in non PlayerBytesLayout. PlayerAds count: ",
    )
)

internal object LoadVideoAdsFingerprint : Fingerprint(
    strings = listOf(
        "TriggerBundle doesn't have the required metadata specified by the trigger ",
        "Ping migration no associated ping bindings for activated trigger: ",
    ),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET, type = "I"),
        methodCall(
            smali = "Landroid/util/SparseArray;->get(I)Ljava/lang/Object;",
            location = MatchAfterImmediately()
        )
    )
)

internal object PlayerBytesAdSchedulingFingerprint : Fingerprint(
    classFingerprint = PlayerBytesAdLayoutFingerprint,
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "Lj$/util/Optional;",
    parameters = listOf("L", "L", "Ljava/lang/String;"),
)

val ibrahimYouTubeAdBlockPatch = patch(
    name = "Ibrahim YouTube ad blocker",
    description = "Blocks YouTube player-byte video ads and prevents ad-slot scheduling."
) {
    compatibleWith(Constants.COMPATIBILITY_YOUTUBE)
    extendWith("extensions/ibrahim-youtube.mpe")

    execute {
        // Prevent the player-byte ad layout from being created.
        PlayerBytesAdLayoutFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->hideVideoAds()Z
                move-result v0
                if-eqz v0, :show_video_ads
                return-void
                :show_video_ads
                nop
            """
        )

        // Keep only the trigger category used to expire the blocked ad slots.
        // This mirrors the upstream ad-blocker behavior and avoids accumulating ad slots.
        LoadVideoAdsFingerprint.let { fingerprint ->
            val categoryField = fingerprint.instructionMatches.first().fieldReference
            val filterTriggersMethod = ImmutableMethod(
                fingerprint.classDef.type,
                "patch_filterVideoAdTriggers",
                listOf(ImmutableMethodParameter("Ljava/util/List;", null, null)),
                "Ljava/util/List;",
                AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
                null,
                null,
                MutableMethodImplementation(6),
            ).also { immutableMethod ->
                val mutableMethod = app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
                    .toMutable(immutableMethod)
                mutableMethod.addInstructionsWithLabels(
                    0,
                    """
                        invoke-static {}, $EXTENSION_CLASS->hideVideoAds()Z
                        move-result v0
                        if-eqz v0, :keep_triggers
                        new-instance v0, Ljava/util/ArrayList;
                        invoke-direct { v0 }, Ljava/util/ArrayList;-><init>()V
                        invoke-interface { p0 }, Ljava/util/List;->iterator()Ljava/util/Iterator;
                        move-result-object v1
                        const/16 v4, 0x6
                        :next_trigger
                        invoke-interface { v1 }, Ljava/util/Iterator;->hasNext()Z
                        move-result v2
                        if-eqz v2, :filtered_triggers
                        invoke-interface { v1 }, Ljava/util/Iterator;->next()Ljava/lang/Object;
                        move-result-object v2
                        check-cast v2, ${categoryField.definingClass}
                        iget v3, v2, $categoryField
                        if-ne v3, v4, :next_trigger
                        invoke-interface { v0, v2 }, Ljava/util/List;->add(Ljava/lang/Object;)Z
                        goto :next_trigger
                        :filtered_triggers
                        return-object v0
                        :keep_triggers
                        return-object p0
                    """
                )
                fingerprint.classDef.methods.add(mutableMethod)
            }

            fingerprint.method.addInstructions(
                0,
                """
                    invoke-static/range { p1 .. p1 }, $filterTriggersMethod
                    move-result-object p1
                """
            )
        }

        // The player waits for the ad slot future before playback begins. Return an empty
        // future so blocking the ad slot does not leave playback waiting forever.
        PlayerBytesAdSchedulingFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->hideVideoAds()Z
                move-result v0
                if-eqz v0, :show_video_ads
                invoke-static {}, Lj$/util/Optional;->empty()Lj$/util/Optional;
                move-result-object v0
                return-object v0
                :show_video_ads
                nop
            """
        )
    }
}
