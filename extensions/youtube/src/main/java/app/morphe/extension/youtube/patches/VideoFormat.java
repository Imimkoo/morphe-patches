/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public final class VideoFormat {

    /**
     * Interface to use obfuscated methods.
     */
    public interface FormatInterface {
        // Methods are added during patching.
        String patch_getMimeType();
        int patch_getWidth();
        int patch_getHeight();
        int patch_getItag();
    }

    /**
     * Injection point.
     */
    public static List<FormatInterface> hookAdaptiveFormats(@Nullable String videoId, @NonNull List<FormatInterface> adaptiveFormats) {
        boolean hasAv1 = false;
        for (FormatInterface format : adaptiveFormats) {
            try {
                String mimeType = format.patch_getMimeType();
                if (mimeType != null && mimeType.regionMatches(true, 0, "video/", 0, 6)
                        && mimeType.toLowerCase(java.util.Locale.ROOT).contains("av01")) {
                    hasAv1 = true;
                    break;
                }
            } catch (Exception ignored) {
                // A malformed format entry must not prevent playback of the remaining formats.
            }
        }
        // Update the codec gate for every response/client. A forced AV1 preference is only
        // exclusive when this particular response contains AV1; otherwise normal fallback works.
        DisableVideoCodecsPatch.observeAdaptiveFormats(videoId, hasAv1);

        return Utils.isNotEmpty(videoId) && !"zzzzzzzzzzz".equals(videoId) && !adaptiveFormats.isEmpty()
                ? privateHookAdaptiveFormats(videoId, adaptiveFormats)
                : adaptiveFormats;
    }

    private static List<FormatInterface> privateHookAdaptiveFormats(@NonNull String videoId, @NonNull List<FormatInterface> adaptiveFormats) {
        // Code added during patching.
        return adaptiveFormats;
    }
}
