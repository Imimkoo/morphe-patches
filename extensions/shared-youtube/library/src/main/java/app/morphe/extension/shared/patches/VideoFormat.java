/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.shared.patches;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** AV1 availability of each player response, kept per video because the feed can load concurrently. */
    private static final Map<String, Boolean> responseHasAv1 =
            Collections.synchronizedMap(new LinkedHashMap<>());

    /** Injection point. */
    public static List<FormatInterface> hookAdaptiveFormats(@Nullable String videoId, @NonNull List<FormatInterface> adaptiveFormats) {
        if (Utils.isNotEmpty(videoId) && !"zzzzzzzzzzz".equals(videoId)) {
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
                    // Ignore malformed entries; do not break the remaining stream response.
                }
            }
            synchronized (responseHasAv1) {
                responseHasAv1.put(videoId, hasAv1);
                while (responseHasAv1.size() > 50) {
                    responseHasAv1.remove(responseHasAv1.keySet().iterator().next());
                }
            }
        }
        return Utils.isNotEmpty(videoId) && !"zzzzzzzzzzz".equals(videoId) && !adaptiveFormats.isEmpty()
                ? privateHookAdaptiveFormats(videoId, adaptiveFormats)
                : adaptiveFormats;
    }

    /** Whether this video's adaptive formats contain an AV1 video track. */
    public static boolean hasAv1VideoFormat(@Nullable String videoId) {
        if (videoId == null) return false;
        synchronized (responseHasAv1) {
            return Boolean.TRUE.equals(responseHasAv1.get(videoId));
        }
    }

    private static List<FormatInterface> privateHookAdaptiveFormats(@NonNull String videoId, @NonNull List<FormatInterface> adaptiveFormats) {
        // Code added during patching.
        return adaptiveFormats;
    }
}
