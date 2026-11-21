/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

/**
 * Pure codec-selection policy, kept separate so all mode combinations can be unit tested.
 */
final class VideoCodecPolicy {
    private VideoCodecPolicy() {
    }

    static boolean allowVP9(String codec) {
        return !"AVC".equals(codec) && !"FORCE_AV1".equals(codec);
    }

    static boolean allowCodec(String codec, String mimeType, String av1MimeType) {
        if ("FORCE_AV1".equals(codec)) {
            return av1MimeType.equalsIgnoreCase(mimeType);
        }
        if ("VP9".equals(codec) || "AVC".equals(codec)) {
            return !av1MimeType.equalsIgnoreCase(mimeType);
        }
        return true;
    }
}
