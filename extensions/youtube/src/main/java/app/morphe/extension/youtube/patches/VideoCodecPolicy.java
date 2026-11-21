/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.youtube.patches;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Pure codec selection and adaptive-format policy, independent of Android APIs for unit testing.
 */
final class VideoCodecPolicy {
    interface DecoderCapabilities {
        boolean supports(int width, int height, int frameRate);
    }

    interface FormatAdapter<T> {
        FormatDescriptor describe(T format);
    }

    static final class FormatDescriptor {
        final String mimeType;
        final int width;
        final int height;
        final int frameRate;

        FormatDescriptor(String mimeType, int width, int height, int frameRate) {
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
        }
    }

    private VideoCodecPolicy() {}

    static boolean isVideoMimeType(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    static boolean isAv1(String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).contains("av01");
    }

    static boolean isAvc(String mimeType) {
        if (mimeType == null) return false;
        String value = mimeType.toLowerCase(Locale.ROOT);
        return value.contains("video/avc") || value.contains("avc1") || value.contains("avc3");
    }

    static boolean isVp9(String mimeType) {
        if (mimeType == null) return false;
        String value = mimeType.toLowerCase(Locale.ROOT);
        return value.contains("vp9") || value.contains("vp09");
    }

    static boolean allowVP9(String codec) {
        return !"AVC".equals(codec) && !"FORCE_AV1".equals(codec);
    }

    static boolean allowCodec(String codec, String mimeType, String av1MimeType) {
        // CodecSupportCheckFingerprint is also called for audio and may pass a null MIME type.
        if (!isVideoMimeType(mimeType)) return true;

        if ("FORCE_AV1".equals(codec)) return isAv1(mimeType) || mimeType.toLowerCase(Locale.ROOT).contains("av01");
        if ("AVC".equals(codec)) return isAvc(mimeType);
        if ("VP9".equals(codec)) return !isAv1(mimeType);
        return true;
    }

    static <T> List<T> filterAdaptiveFormats(
            List<T> formats,
            String codec,
            DecoderCapabilities decoder,
            FormatAdapter<T> adapter) {
        if ("FORCE_AV1".equals(codec)) {
            return formats.stream().filter(format -> {
                FormatDescriptor descriptor = adapter.describe(format);
                return !isVideoMimeType(descriptor.mimeType) || isAv1(descriptor.mimeType);
            }).collect(Collectors.toList());
        }

        if (!"PREFER_AV1".equals(codec) || decoder == null) return formats;

        // Prefer supported AV1 formats by placing them first, but retain every fallback
        // format. Filtering alternatives here would turn PREFER_AV1 into FORCE_AV1 and
        // could leave playback with no usable stream if AV1 negotiation later fails.
        List<T> preferredAv1 = new java.util.ArrayList<>();
        List<T> fallbackFormats = new java.util.ArrayList<>();

        for (T format : formats) {
            FormatDescriptor descriptor = adapter.describe(format);
            if (isVideoMimeType(descriptor.mimeType) && isAv1(descriptor.mimeType)
                    && decoder.supports(descriptor.width, descriptor.height, descriptor.frameRate)) {
                preferredAv1.add(format);
            } else {
                fallbackFormats.add(format);
            }
        }

        if (preferredAv1.isEmpty()) return formats;

        preferredAv1.addAll(fallbackFormats);
        return preferredAv1;
    }
}