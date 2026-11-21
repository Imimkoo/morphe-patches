/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure codec-selection policy, kept separate so all mode combinations can be unit tested.
 */
final class VideoCodecPolicy {
    private VideoCodecPolicy() {
    }

    static boolean allowVP9(String codec) {
        return !"AVC".equals(codec) && !"FORCE_AV1".equals(codec);
    }

    static boolean isAv1Mime(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("av01");
    }

    static boolean isAv1Itag(int itag) {
        return (itag >= 394 && itag <= 401) || itag == 571 || (itag >= 694 && itag <= 701);
    }

    static boolean isAv1Format(VideoFormat.FormatInterface format) {
        return format != null && (isAv1Mime(format.patch_getMimeType()) || isAv1Itag(format.patch_getItag()));
    }

    static boolean isCompetingVideoMime(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        return mime.contains("avc1") || mime.contains("avc3")
                || mime.contains("vp9") || mime.contains("vp09") || mime.contains("vp8")
                || mime.contains("hvc1") || mime.contains("hev1");
    }

    static boolean isAudioMime(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        if (mime.startsWith("audio/")) {
            return true;
        }
        if (isVideoMime(mime)) {
            return false;
        }
        return mime.contains("mp4a") || mime.contains("opus") || mime.contains("vorbis")
                || mime.contains("flac") || mime.contains("ac-3") || mime.contains("ec-3")
                || mime.contains("alac");
    }

    static boolean isVideoMime(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        return mime.startsWith("video/") || mime.contains("av01")
                || mime.contains("avc1") || mime.contains("avc3")
                || mime.contains("vp9") || mime.contains("vp09") || mime.contains("vp8")
                || mime.contains("hvc1") || mime.contains("hev1");
    }

    /**
     * Applies codec preference to YouTube's actual adaptive-format list.
     *
     * PREFER_AV1 only removes competing video formats when an AV1 decoder and at least one
     * AV1 video format are both present. Audio-only formats are always retained.
     * FORCE_AV1 removes every non-AV1 video/unknown format while retaining audio.
     */
    static List<VideoFormat.FormatInterface> filterAdaptiveFormats(
            String codec,
            List<VideoFormat.FormatInterface> formats,
            boolean av1DecoderAvailable
    ) {
        if (formats == null || formats.isEmpty()) {
            return formats;
        }

        final boolean preferAv1 = "PREFER_AV1".equals(codec);
        final boolean forceAv1 = "FORCE_AV1".equals(codec);
        if (!preferAv1 && !forceAv1) {
            return formats;
        }

        if (preferAv1 && !av1DecoderAvailable) {
            return formats;
        }

        boolean hasAv1Video = false;
        for (VideoFormat.FormatInterface format : formats) {
            String mimeType = format == null ? null : format.patch_getMimeType();
            if (isAv1Format(format) && !isAudioMime(mimeType)) {
                hasAv1Video = true;
                break;
            }
        }

        // Prefer mode must remain a true fallback mode when this response has no AV1 video.
        if (preferAv1 && !hasAv1Video) {
            return formats;
        }

        List<VideoFormat.FormatInterface> filtered = new ArrayList<>(formats.size());
        for (VideoFormat.FormatInterface format : formats) {
            if (format == null) {
                if (!forceAv1) {
                    filtered.add(null);
                }
                continue;
            }

            String mimeType = format.patch_getMimeType();
            if (isAudioMime(mimeType)) {
                filtered.add(format);
            } else if (isVideoMime(mimeType) || isAv1Format(format)) {
                if (isAv1Format(format)) {
                    filtered.add(format);
                }
            } else if (!forceAv1) {
                // Keep unknown non-video metadata in prefer mode; force mode fails closed.
                filtered.add(format);
            }
        }

        return filtered;
    }

    static boolean allowCodec(String codec, String mimeType, String av1MimeType) {
        if ("FORCE_AV1".equals(codec)) {
            return isAv1Mime(mimeType);
        }
        if ("AVC".equals(codec)) {
            return "video/avc".equalsIgnoreCase(mimeType);
        }
        if ("VP9".equals(codec)) {
            return !isAv1Mime(mimeType);
        }
        return true;
    }
}
