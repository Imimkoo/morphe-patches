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
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.os.Build;
import android.view.Display;

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
     * @return The default codec setting, which is VP9 if the device cannot hardware decode AV1.
     */
    public static VideoCodec getDefaultVideoCodec() {
        return hasAv1Decoder() ? VideoCodec.DEFAULT : VideoCodec.VP9;
    }

    private static volatile List<VideoCapabilities> av1DecoderCapabilities;

    /**
     * Enumerates every regular AV1 decoder, including hardware and software implementations.
     */
    private static List<VideoCapabilities> getAv1DecoderCapabilities() {
        List<VideoCapabilities> cached = av1DecoderCapabilities;
        if (cached != null) {
            return cached;
        }

        List<VideoCapabilities> found = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                    if (info.isEncoder()) {
                        continue;
                    }
                    for (String type : info.getSupportedTypes()) {
                        if (MIMETYPE_VIDEO_AV1.equalsIgnoreCase(type)) {
                            CodecCapabilities capabilities = info.getCapabilitiesForType(type);
                            VideoCapabilities video = capabilities.getVideoCapabilities();
                            if (video != null) {
                                found.add(video);
                                Logger.printDebug(() -> "Found AV1 decoder: " + info.getName()
                                        + ", hardware=" + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                        && info.isHardwareAccelerated()));
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                Logger.printException(() -> "getAv1DecoderCapabilities failure", ex);
            }
        }

        av1DecoderCapabilities = found;
        Logger.printDebug(() -> found.isEmpty()
                ? "No AV1 decoder found"
                : "AV1 decoders found: " + found.size());
        return found;
    }

    private static boolean hasAv1Decoder() {
        return !getAv1DecoderCapabilities().isEmpty();
    }

    private static boolean supportsAv1Format(int width, int height, int frameRate) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        for (VideoCapabilities capabilities : getAv1DecoderCapabilities()) {
            try {
                boolean supported = frameRate > 0
                        ? capabilities.areSizeAndRateSupported(width, height, frameRate)
                        : capabilities.isSizeSupported(width, height);
                if (supported) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // Try the next decoder; dimensions may exceed this decoder's advertised bounds.
            }
        }
        return false;
    }

    /**
     * Injection point called from VideoFormatPatch before ExoPlayer receives adaptive formats.
     */
    public static List<VideoFormat.FormatInterface> filterAdaptiveFormats(
            @Nullable String videoId, List<VideoFormat.FormatInterface> adaptiveFormats) {
        VideoCodec codec = getVideoCodec();
        return VideoCodecPolicy.filterAdaptiveFormats(
                adaptiveFormats,
                codec.name(),
                DisableVideoCodecsPatch::supportsAv1Format,
                format -> new VideoCodecPolicy.FormatDescriptor(
                        format.patch_getMimeType(),
                        format.patch_getWidth(),
                        format.patch_getHeight(),
                        0 // Frame rate is not exposed by the target format fingerprint; validate dimensions only.
                )
        );
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
