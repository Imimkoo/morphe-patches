/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Pure, unit-testable video codec selection and stream-filtering policy.
 */
final class VideoCodecPolicy {

    static final String CODEC_DEFAULT = "DEFAULT";
    static final String CODEC_PREFER_AV1 = "PREFER_AV1";
    static final String CODEC_FORCE_AV1 = "FORCE_AV1";
    static final String CODEC_VP9 = "VP9";
    static final String CODEC_AVC = "AVC";

    @FunctionalInterface
    interface Av1SizeSupport {
        boolean isSupported(int width, int height);
    }

    private VideoCodecPolicy() {
    }

    static boolean isVideoMimeType(@Nullable String mimeType) {
        return mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    static boolean isAv1MimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lower = mimeType.toLowerCase(Locale.ROOT);
        return lower.startsWith("video/") && lower.contains("av01");
    }

    static boolean isAvcMimeType(@Nullable String mimeType) {
        if (mimeType == null) {
            return false;
        }
        String lower = mimeType.toLowerCase(Locale.ROOT);
        return lower.startsWith("video/") && lower.contains("avc");
    }

    static boolean allowVP9(String codecName) {
        return !CODEC_AVC.equals(codecName) && !CODEC_FORCE_AV1.equals(codecName);
    }

    static boolean allowCodec(String codecName, @Nullable String mimeType) {
        // Never block audio or non-video MIME types.
        if (!isVideoMimeType(mimeType)) {
            return true;
        }
        if (CODEC_VP9.equals(codecName)) {
            return !isAv1MimeType(mimeType);
        }
        if (CODEC_AVC.equals(codecName)) {
            return isAvcMimeType(mimeType);
        }
        if (CODEC_FORCE_AV1.equals(codecName)) {
            return isAv1MimeType(mimeType);
        }
        return true;
    }

    static boolean isPlayableAv1Format(@Nullable String mimeType,
                                       int width,
                                       int height,
                                       boolean hasAv1Decoder,
                                       Av1SizeSupport sizeSupport) {
        if (!hasAv1Decoder || !isAv1MimeType(mimeType)) {
            return false;
        }
        if (width <= 0 || height <= 0) {
            return true;
        }
        return sizeSupport.isSupported(width, height);
    }

    static <T> List<T> filterAdaptiveFormats(String codecName,
                                             List<T> adaptiveFormats,
                                             Function<T, String> mimeTypeFn,
                                             ToIntFunction<T> widthFn,
                                             ToIntFunction<T> heightFn,
                                             boolean hasAv1Decoder,
                                             Av1SizeSupport sizeSupport) {
        if (adaptiveFormats == null || adaptiveFormats.isEmpty()) {
            return adaptiveFormats;
        }
        if (!CODEC_PREFER_AV1.equals(codecName) && !CODEC_FORCE_AV1.equals(codecName)) {
            return adaptiveFormats;
        }

        if (CODEC_PREFER_AV1.equals(codecName)) {
            if (!hasAv1Decoder) {
                return adaptiveFormats;
            }

            boolean hasPlayableAv1 = false;
            for (T format : adaptiveFormats) {
                String mimeType = mimeTypeFn.apply(format);
                if (isPlayableAv1Format(mimeType, widthFn.applyAsInt(format), heightFn.applyAsInt(format),
                        true, sizeSupport)) {
                    hasPlayableAv1 = true;
                    break;
                }
            }

            if (!hasPlayableAv1) {
                return adaptiveFormats;
            }

            List<T> filtered = new ArrayList<>(adaptiveFormats.size());
            for (T format : adaptiveFormats) {
                String mimeType = mimeTypeFn.apply(format);
                if (!isVideoMimeType(mimeType)
                        || isPlayableAv1Format(mimeType, widthFn.applyAsInt(format), heightFn.applyAsInt(format),
                        true, sizeSupport)) {
                    filtered.add(format);
                }
            }
            return filtered;
        }

        // FORCE_AV1: keep non-video (audio) formats and AV1 video formats only; never fall back to VP9/AVC.
        List<T> forced = new ArrayList<>(adaptiveFormats.size());
        for (T format : adaptiveFormats) {
            String mimeType = mimeTypeFn.apply(format);
            if (!isVideoMimeType(mimeType) || isAv1MimeType(mimeType)) {
                forced.add(format);
            }
        }
        return forced;
    }
}