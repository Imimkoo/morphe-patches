/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches/pull/2634
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-patches/commit/724e6d61b2ecd868c1a9a37d465a688e83a74799
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.SharedYouTubeSettings;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch;
import app.morphe.extension.youtube.settings.Settings;

@SuppressWarnings({"unused", "deprecation", "RedundantSuppression"})
public class DisableVideoCodecsPatch {

    public enum VideoCodec {
        DEFAULT,
        PREFER_AV1,
        FORCE_AV1,
        VP9,
        AVC
    }

    /**
     * Same as MediaFormat.MIMETYPE_VIDEO_AV1, which is only available with Android 10 and newer.
     */
    private static final String MIMETYPE_VIDEO_AV1 = "video/av01";

    private static final int[] EMPTY_ARRAY_INT = new int[0];

    private static volatile Boolean av1DecoderAvailable;

    /**
     * HDR types YouTube checks via {@link Display.HdrCapabilities#getSupportedHdrTypes()}.
     * HLG is the type HyperOS often omits even when the decoder can play YouTube HDR.
     */
    private static final int[] HDR_TYPES = getHdrTypes();

    /**
     * @return Equivalent of Display.HdrCapabilities.HDR_TYPES
     */
    private static int[] getHdrTypes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new int[]{
                    Display.HdrCapabilities.HDR_TYPE_HLG,
                    Display.HdrCapabilities.HDR_TYPE_HDR10,
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS,
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
            };
        }

        return new int[]{
                Display.HdrCapabilities.HDR_TYPE_HLG,
                Display.HdrCapabilities.HDR_TYPE_HDR10,
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION
        };
    }

    /**
     * Injection point.
     */
    public static int[] overrideSupportedHdrTypes(Display.HdrCapabilities capabilities) {
        if (Settings.DISABLE_HDR_VIDEO.get()) {
            return EMPTY_ARRAY_INT;
        }

        int[] original = capabilities == null
                ? EMPTY_ARRAY_INT
                : capabilities.getSupportedHdrTypes();
        if (!Settings.FORCE_HDR_VIDEO.get()) {
            return original;
        }

        return unionHdrTypes(original);
    }

    private static int[] unionHdrTypes(int[] original) {
        if (original.length == 0) {
            return HDR_TYPES.clone();
        }

        int missingCount = 0;
        for (int forced : HDR_TYPES) {
            if (!containsHdrType(original, forced)) {
                missingCount++;
            }
        }
        if (missingCount == 0) {
            return original;
        }

        int[] result = new int[original.length + missingCount];
        System.arraycopy(original, 0, result, 0, original.length);
        int index = original.length;
        for (int forced : HDR_TYPES) {
            if (!containsHdrType(original, forced)) {
                result[index++] = forced;
            }
        }
        return result;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean containsHdrType(int[] types, int type) {
        for (int existing : types) {
            if (existing == type) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return If AVC can be forced, which needs spoofing to be off or a spoof client that has AVC formats.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isAvcAvailable() {
        return !SpoofVideoStreamsPatch.isPatchIncluded()
                || !SharedYouTubeSettings.SPOOF_VIDEO_STREAMS.get() || List.of(
                ClientType.ANDROID_CREATOR,
                ClientType.ANDROID_VR_DASH,
                ClientType.ANDROID_VR_SABR,
                ClientType.ANDROID_VR_EXPERIMENTAL,
                ClientType.VISIONOS_1_02,
                ClientType.VISIONOS_1_06).contains(Settings.SPOOF_VIDEO_STREAMS_CLIENT_TYPE.get());
    }

    /**
     * @return Whether Android exposes an AV1 decoder, hardware or software.
     */
    private static boolean hasAv1Decoder() {
        Boolean cached = av1DecoderAvailable;
        if (cached != null) {
            return cached;
        }

        boolean available = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                    if (info.isEncoder()) {
                        continue;
                    }
                    for (String type : info.getSupportedTypes()) {
                        if (MIMETYPE_VIDEO_AV1.equalsIgnoreCase(type)) {
                            try {
                                info.getCapabilitiesForType(type);
                                Logger.printDebug(() -> "Found AV1 decoder: " + info.getName());
                                available = true;
                                break;
                            } catch (Exception ignored) {
                                // A codec may advertise an alias without usable decoder capabilities.
                            }
                        }
                    }
                    if (available) {
                        break;
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "hasAv1Decoder failure", ex);
            }
        }

        av1DecoderAvailable = available;
        final boolean decoderFound = available;
        Logger.printDebug(() -> "AV1 decoder available: " + decoderFound);
        return decoderFound;
    }

    /**
     * @return The default codec setting. Prefer AV1 when Android exposes a decoder;
     * otherwise use VP9 as the efficient fallback.
     */
    public static VideoCodec getDefaultVideoCodec() {
        return hasAv1Decoder() ? VideoCodec.PREFER_AV1 : VideoCodec.VP9;
    }

    /**
     * Injection point called while YouTube builds its adaptive format list.
     */
    public static List<VideoFormat.FormatInterface> hookAdaptiveFormats(
            @Nullable String videoId,
            @NonNull List<VideoFormat.FormatInterface> adaptiveFormats
    ) {
        final VideoCodec codec = getVideoCodec();
        final boolean decoderAvailable = hasAv1Decoder();
        List<VideoFormat.FormatInterface> formatsToFilter = adaptiveFormats;

        // Avoid preferring AV1 sizes that no installed AV1 decoder can actually handle.
        if (decoderAvailable && (codec == VideoCodec.PREFER_AV1 || codec == VideoCodec.FORCE_AV1)) {
            formatsToFilter = filterUnsupportedAv1Sizes(adaptiveFormats);
        }

        final List<VideoFormat.FormatInterface> filtered = VideoCodecPolicy.filterAdaptiveFormats(
                codec.name(), formatsToFilter, decoderAvailable
        );

        if (filtered != adaptiveFormats) {
            int av1Count = 0;
            int videoCount = 0;
            for (VideoFormat.FormatInterface format : filtered) {
                if (format == null) {
                    continue;
                }
                String mimeType = format.patch_getMimeType();
                if (VideoCodecPolicy.isVideoMime(mimeType)) {
                    videoCount++;
                    if (VideoCodecPolicy.isAv1Format(format)) {
                        av1Count++;
                    }
                }
            }
            final int loggedVideoCount = videoCount;
            final int loggedAv1Count = av1Count;
            Logger.printInfo(() -> "AV1 format policy for " + videoId + ": "
                    + codec + ", video formats " + loggedVideoCount + ", AV1 " + loggedAv1Count
                    + ", total " + filtered.size());
        }
        return filtered;
    }

    private static List<VideoFormat.FormatInterface> filterUnsupportedAv1Sizes(
            @NonNull List<VideoFormat.FormatInterface> formats
    ) {
        List<VideoFormat.FormatInterface> result = new ArrayList<>(formats.size());
        for (VideoFormat.FormatInterface format : formats) {
            if (format == null || !VideoCodecPolicy.isAv1Format(format)
                    || isAv1FormatDecodable(format)) {
                result.add(format);
            } else {
                Logger.printDebug(() -> "Skipping unsupported AV1 format: itag="
                        + format.patch_getItag() + ", size="
                        + format.patch_getWidth() + "x" + format.patch_getHeight());
            }
        }
        return result;
    }

    private static boolean isAv1FormatDecodable(VideoFormat.FormatInterface format) {
        final int width = format.patch_getWidth();
        final int height = format.patch_getHeight();
        if (width <= 0 || height <= 0) {
            return true;
        }

        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (!MIMETYPE_VIDEO_AV1.equalsIgnoreCase(type)) {
                        continue;
                    }
                    try {
                        MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type);
                        MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
                        if (videoCapabilities == null || videoCapabilities.isSizeSupported(width, height)) {
                            return true;
                        }
                    } catch (Exception ignored) {
                        // Continue checking other AV1 decoders.
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "isAv1FormatDecodable failure", ex);
        }

        return false;
    }

    private static boolean hasHardwareAv1Decoder() {
        // Android 9 and older cannot report if a decoder is hardware accelerated,
        // and devices with a hardware AV1 decoder all ship with Android 10 or newer.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }

        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder() || !info.isHardwareAccelerated()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (MIMETYPE_VIDEO_AV1.equalsIgnoreCase(type)) {
                        Logger.printDebug(() -> "Found hardware AV1 decoder: " + info.getName());
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "hasHardwareAv1Decoder failure", ex);
        }

        Logger.printDebug(() -> "No hardware AV1 decoder found");
        return false;
    }

    private static VideoCodec resolveVideoCodec() {
        VideoCodec codec = Settings.VIDEO_CODEC.get();
        if (codec == VideoCodec.AVC && !isAvcAvailable()) {
            return VideoCodec.VP9;
        }
        return codec;
    }

    /**
     * @return The current codec setting. Read it on every call so changing the setting
     * takes effect immediately without requiring an app restart.
     */
    public static VideoCodec getVideoCodec() {
        return resolveVideoCodec();
    }

    /**
     * Injection point.
     */
    public static boolean allowVP9() {
        final VideoCodec codec = getVideoCodec();
        final boolean allowed = VideoCodecPolicy.allowVP9(codec.name());
        if (!allowed) {
            Logger.printVerbose(() -> "VP9 not allowed");
        }
        return allowed;
    }

    /**
     * Injection point.
     */
    public static boolean allowCodec(String mimeType) {
        final VideoCodec codec = getVideoCodec();
        final boolean allowed = VideoCodecPolicy.allowCodec(codec.name(), mimeType, MIMETYPE_VIDEO_AV1);
        if (!allowed) {
            Logger.printVerbose(() -> "Codec not allowed: " + mimeType);
        }
        return allowed;
    }
}
