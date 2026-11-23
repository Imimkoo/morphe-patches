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

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags

internal object HDRCapabilityFingerprint : Fingerprint(
    filters = listOf(
        methodCall(
            definingClass = $$"Landroid/view/Display$HdrCapabilities;",
            name = "getSupportedHdrTypes",
        )
    ),
    custom = { _, classDef ->
        !classDef.type.startsWith("Lapp/morphe/")
    }
)

internal object Vp9CapabilityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Z",
    strings = listOf(
        "vp9_supported",
        "video/x-vnd.on2.vp9"
    )
)

/**
 * Codec support check in the class of [Vp9CapabilityFingerprint]. The second parameter is the mime type.
 */
internal object CodecSupportCheckFingerprint : Fingerprint(
    classFingerprint = Vp9CapabilityFingerprint,
    returnType = "Z",
    parameters = listOf(
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Z",
        "Ljava/util/Set;",
        "Ljava/util/Set;",
        "L",
        "I"
    )
)


/**
 * YouTube's embedded dav1d eligibility check. The original method gates AV1
 * behind device/remote capability checks even though the APK bundles dav1dJNI.
 */
internal object EmbeddedDav1dFormatFingerprint : Fingerprint(
    definingClass = "Lsun;",
    name = "bw",
    returnType = "Z",
    parameters = listOf("Ledc;"),
    strings = listOf("video/av01")
)

/**
 * Renderer-selection gate. Keep the existing AV1-vs-other-format decision,
 * but do not let the rollout/device gate suppress the APK-bundled decoder.
 */
internal object EmbeddedDav1dSelectionFingerprint : Fingerprint(
    definingClass = "Ldefpackage/sun;",
    name = "aN",
    returnType = "Z",
    parameters = listOf("Ldefpackage/edc;")
)


/** Tracks the renderer selected by YouTube's AV1/MediaCodec splice renderer. */
internal object EmbeddedDav1dRendererStateFingerprint : Fingerprint(
    definingClass = "Lsuq;",
    name = "aE",
    returnType = "V",
    parameters = listOf("Laovd;")
)
