/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for license and attribution details.
 */

package app.morphe.patches.youtube.video.buffer

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/** ExoPlayer's load-control shouldContinueLoading method. */
internal object ShouldContinueLoadingFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    parameters = listOf("L"),
    filters = listOf(
        fieldAccess(opcode = Opcode.IGET_WIDE, type = "J"),
        opcode(Opcode.CMP_LONG, location = MatchAfterWithin(3)),
        literal(120000),
        literal(15000L)
    )
)
