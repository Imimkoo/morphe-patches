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
import app.morphe.extension.youtube.patches.VideoFormat.FormatInterface;
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
        return hasHardwareAv1Decoder() ? VideoCodec.DEFAULT : VideoCodec.VP9;
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

    public static boolean hasAnyAv1Decoder() {
        return getAv1DecoderState().hasDecoder;
    }

    private static final class Av1DecoderState {
        final boolean hasDecoder;
        final List<MediaCodecInfo.VideoCapabilities> videoCapabilities;

        Av1DecoderState(boolean hasDecoder, List<MediaCodecInfo.VideoCapabilities> videoCapabilities) {
            this.hasDecoder = hasDecoder;
            this.videoCapabilities = videoCapabilities;
        }
    }

    @Nullable
    private static volatile Av1DecoderState cachedAv1DecoderState;

    private static Av1DecoderState getAv1DecoderState() {
        Av1DecoderState state = cachedAv1DecoderState;
        if (state != null) {
            return state;
        }

        boolean found = false;
        List<MediaCodecInfo.VideoCapabilities> capsList = new ArrayList<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (MIMETYPE_VIDEO_AV1.equalsIgnoreCase(type)) {
                        found = true;
                        try {
                            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                            MediaCodecInfo.VideoCapabilities videoCaps = caps != null ? caps.getVideoCapabilities() : null;
                            if (videoCaps != null) {
                                capsList.add(videoCaps);
                            }
                        } catch (Exception ex) {
                            Logger.printDebug(() -> "Failed to read VideoCapabilities for " + info.getName());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "getAv1DecoderState failure", ex);
        }

        state = new Av1DecoderState(found, capsList);
        cachedAv1DecoderState = state;
        return state;
    }

    private static boolean isAv1SizeSupported(int width, int height) {
        Av1DecoderState state = getAv1DecoderState();
        if (!state.hasDecoder) {
            return false;
        }
        if (state.videoCapabilities.isEmpty() || width <= 0 || height <= 0) {
            return true;
        }
        for (MediaCodecInfo.VideoCapabilities videoCaps : state.videoCapabilities) {
            try {
                if (videoCaps.isSizeSupported(width, height)
                        || videoCaps.isSizeSupported(height, width)) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
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
     * @return The codec in use, which is VP9 if AVC is selected but cannot be forced.
     */
    public static VideoCodec getVideoCodec() {
        return resolveVideoCodec();
    }

    /**
     * Injection point.
     */
    public static boolean allowVP9() {
        final boolean allowed = VideoCodecPolicy.allowVP9(getVideoCodec().name());
        if (!allowed) {
            Logger.printVerbose(() -> "VP9 not allowed");
        }
        return allowed;
    }

    /**
     * Injection point.
     */
    public static boolean allowCodec(String mimeType) {
        final boolean allowed = VideoCodecPolicy.allowCodec(getVideoCodec().name(), mimeType);
        if (!allowed) {
            Logger.printVerbose(() -> "Codec not allowed: " + mimeType);
        }
        return allowed;
    }

    /**
     * Injection point.
     */
    public static List<FormatInterface> filterAdaptiveFormats(@NonNull String videoId,
                                                              @NonNull List<FormatInterface> adaptiveFormats) {
        final VideoCodec codec = getVideoCodec();
        if (codec != VideoCodec.PREFER_AV1 && codec != VideoCodec.FORCE_AV1) {
            return adaptiveFormats;
        }

        try {
            Av1DecoderState state = getAv1DecoderState();
            List<FormatInterface> filtered = VideoCodecPolicy.filterAdaptiveFormats(
                    codec.name(),
                    adaptiveFormats,
                    FormatInterface::patch_getMimeType,
                    FormatInterface::patch_getWidth,
                    FormatInterface::patch_getHeight,
                    state.hasDecoder,
                    DisableVideoCodecsPatch::isAv1SizeSupported
            );
            Logger.printDebug(() -> "AV1 filter (" + codec + ") for " + videoId
                    + ": " + adaptiveFormats.size() + " -> " + filtered.size());
            return filtered;
        } catch (Exception ex) {
            Logger.printException(() -> "filterAdaptiveFormats failure", ex);
            return adaptiveFormats;
        }
    }
}
