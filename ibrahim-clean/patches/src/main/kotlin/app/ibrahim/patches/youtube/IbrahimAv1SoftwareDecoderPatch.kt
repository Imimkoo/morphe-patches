package app.ibrahim.patches.youtube

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.patch.patch

private const val EXTENSION_CLASS = "Lapp/ibrahim/youtube/IbrahimAv1Support;"

/**
 * YouTube 21.30.216 contains Media3's dav1d JNI decoder and renderer.
 * These hooks only make that already-bundled software path eligible when it reports
 * itself available. They do not change stream requests, codec priorities, or fallbacks.
 */
internal object EmbeddedDav1dFormatFingerprint : Fingerprint(
    definingClass = "Lsun;",
    name = "bw",
    returnType = "Z",
    parameters = listOf("Ledc;"),
    strings = listOf("video/av01")
)

internal object EmbeddedDav1dSelectionFingerprint : Fingerprint(
    definingClass = "Lsun;",
    name = "aN",
    returnType = "Z",
    parameters = listOf("Ledc;")
)

val ibrahimAv1SoftwareDecoderPatch = patch(
    name = "Ibrahim AV1 software decoder support",
    description = "Enables YouTube's bundled dav1d software renderer when available; leaves YouTube's stream and codec selection unchanged."
) {
    compatibleWith(Constants.COMPATIBILITY_YOUTUBE)
    extendWith("extensions/ibrahim-youtube.mpe")

    execute {
        // AV1-format eligibility: only override YouTube's own gate when the app's
        // bundled dav1d library and renderer are present and report available.
        EmbeddedDav1dFormatFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->isBundledDav1dAvailable()Z
                move-result v0
                if-eqz v0, :ibrahim_keep_original_format_check
                if-eqz p1, :ibrahim_keep_original_format_check
                iget-object v0, p1, Ledc;->q:Ljava/lang/String;
                const-string v1, "video/av01"
                invoke-virtual {v1, v0}, Ljava/lang/String;->equalsIgnoreCase(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :ibrahim_keep_original_format_check
                const/4 v0, 0x1
                return v0
                :ibrahim_keep_original_format_check
                nop
            """
        )

        // The internal renderer-selection gate delegates to the patched format check
        // only when dav1d is available. Otherwise the original bytecode remains in control.
        EmbeddedDav1dSelectionFingerprint.method.addInstructionsWithLabels(
            0,
            """
                invoke-static {}, $EXTENSION_CLASS->isBundledDav1dAvailable()Z
                move-result v0
                if-eqz v0, :ibrahim_keep_original_renderer_check
                invoke-direct {p0, p1}, Lsun;->bw(Ledc;)Z
                move-result v0
                if-eqz v0, :ibrahim_keep_original_renderer_check
                return v0
                :ibrahim_keep_original_renderer_check
                nop
            """
        )
    }
}
