package app.ibrahim.patches.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.anyInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.patch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val BUFFER_POLICY_CLASS = "Lapp/ibrahim/youtube/IbrahimBufferPolicy;"
private const val BUFFER_BUTTON_CLASS = "Lapp/ibrahim/youtube/IbrahimBufferControlButton;"

internal object ShouldContinueLoadingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        // Load-control buffered duration and its stop/resume comparisons.
        fieldAccess(opcode = Opcode.IGET_WIDE, type = "J"),
        opcode(Opcode.CMP_LONG, location = MatchAfterWithin(3)),
        literal(120000),
        literal(15000L)
    )
)

internal object ExploderUIFullscreenButtonParentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    filters = listOf(resourceLiteral(ResourceType.ID, "time_bar_live_label"))
)

internal object ExploderUIFullscreenButtonFingerprint : Fingerprint(
    classFingerprint = ExploderUIFullscreenButtonParentFingerprint,
    filters = listOf(
        resourceLiteral(ResourceType.ID, "fullscreen_button"),
        app.morphe.patcher.methodCall(
            smali = "Landroid/view/View;->findViewById(I)Landroid/view/View;"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT)
    )
)

val ibrahimSmartBufferPatch = patch(
    name = "Ibrahim smart buffer control",
    description = "Adds a native Ibrahim control for forward-buffer target and resume thresholds."
) {
    compatibleWith(Constants.COMPATIBILITY_YOUTUBE)
    extendWith("extensions/ibrahim-youtube.mpe")

    execute {
        // The player's own shouldContinueLoading() uses this duration to decide whether
        // to request more media. Saturating it while throttled stops future loading without
        // changing the selected format, decoder, player implementation, or seek position.
        ShouldContinueLoadingFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val implementation = requireNotNull(method.implementation) {
                "YouTube shouldContinueLoading has no bytecode implementation"
            }
            val durationField = fingerprint.instructionMatches.first().fieldReference
            val bufferedDurationReads = implementation.instructions
                .mapIndexedNotNull { index, instruction ->
                    val referencedField = (instruction as? ReferenceInstruction)?.reference
                    if (referencedField == durationField) index else null
                }
                .asReversed()

            bufferedDurationReads.forEach { bufferedIndex ->
                val register = method.getInstruction<TwoRegisterInstruction>(bufferedIndex).registerA
                method.addInstructions(
                    bufferedIndex + 1,
                    """
                        invoke-static/range { v$register .. v${register + 1} }, $BUFFER_POLICY_CLASS->apply(J)J
                        move-result-wide v$register
                    """
                )
            }
        }

        // Add a single native player-row entry. The extension adds its own TextView and
        // does not depend on Morphe's PlayerOverlayButton or any other player-control patch.
        ExploderUIFullscreenButtonFingerprint.let { fingerprint ->
            val match = fingerprint.instructionMatches.last()
            val register = match.getInstruction<OneRegisterInstruction>().registerA
            fingerprint.method.addInstruction(
                match.index + 1,
                "invoke-static { v$register }, $BUFFER_BUTTON_CLASS->initializeButton(Landroid/view/View;)V"
            )
        }
    }
}
