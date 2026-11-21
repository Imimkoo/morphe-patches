/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */
package app.morphe.extension.youtube.patches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class VideoCodecPolicyTest {
    private static final String AV1 = "video/webm; codecs=\"av01.0.08M.08\"";
    private static final String VP9 = "video/webm; codecs=\"vp09.00.51.08\"";
    private static final String AVC = "video/mp4; codecs=\"avc1.640028\"";
    private static final String AUDIO = "audio/webm; codecs=\"opus\"";

    private static final class Format {
        final String mime;
        final int width, height, fps;
        Format(String mime, int width, int height, int fps) {
            this.mime = mime; this.width = width; this.height = height; this.fps = fps;
        }
    }

    private static final VideoCodecPolicy.FormatAdapter<Format> ADAPTER =
            format -> new VideoCodecPolicy.FormatDescriptor(format.mime, format.width, format.height, format.fps);

    private static final VideoCodecPolicy.DecoderCapabilities FULL_HD_60 =
            (width, height, fps) -> width <= 1920 && height <= 1080 && (fps <= 60);

    @Test public void audioAndNullMimeTypesAreAlwaysAllowedInEveryMode() {
        for (String mode : List.of("DEFAULT", "PREFER_AV1", "FORCE_AV1", "VP9", "AVC")) {
            assertTrue(mode, VideoCodecPolicy.allowCodec(mode, "audio/mp4a-latm", "video/av01"));
            assertTrue(mode, VideoCodecPolicy.allowCodec(mode, AUDIO, "video/av01"));
            assertTrue(mode, VideoCodecPolicy.allowCodec(mode, null, "video/av01"));
        }
    }

    @Test public void codecModesFilterVideoMimeTypesCorrectly() {
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", AV1, "video/av01"));
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", VP9, "video/av01"));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", VP9, "video/av01"));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", AVC, "video/av01"));
        assertTrue(VideoCodecPolicy.allowCodec("FORCE_AV1", AV1, "video/av01"));
        assertFalse(VideoCodecPolicy.allowCodec("VP9", AV1, "video/av01"));
        assertTrue(VideoCodecPolicy.allowCodec("AVC", AVC, "video/av01"));
        assertFalse(VideoCodecPolicy.allowCodec("AVC", VP9, "video/av01"));
    }

    @Test public void preferAv1RemovesOtherVideoCodecsWhenPlayableAv1ExistsAndKeepsAudio() {
        Format av1 = new Format(AV1, 1920, 1080, 60);
        Format vp9 = new Format(VP9, 1920, 1080, 60);
        Format avc = new Format(AVC, 1280, 720, 30);
        Format audio = new Format(AUDIO, 0, 0, 0);
        List<Format> result = VideoCodecPolicy.filterAdaptiveFormats(
                List.of(av1, vp9, avc, audio), "PREFER_AV1", FULL_HD_60, ADAPTER);
        assertEquals(List.of(av1, audio), result);
    }

    @Test public void preferAv1FallsBackWhenAv1IsMissingOrDecoderIsMissing() {
        Format vp9 = new Format(VP9, 1920, 1080, 60);
        Format audio = new Format(AUDIO, 0, 0, 0);
        List<Format> original = List.of(vp9, audio);
        assertSame(original, VideoCodecPolicy.filterAdaptiveFormats(original, "PREFER_AV1", FULL_HD_60, ADAPTER));
        Format av1 = new Format(AV1, 1280, 720, 30);
        List<Format> withAv1 = List.of(av1, vp9, audio);
        assertSame(withAv1, VideoCodecPolicy.filterAdaptiveFormats(withAv1, "PREFER_AV1", null, ADAPTER));
    }

    @Test public void preferAv1FallsBackWhenAllAv1ResolutionsOrFrameRatesAreUnsupported() {
        Format av1 = new Format(AV1, 3840, 2160, 120);
        Format vp9 = new Format(VP9, 1920, 1080, 60);
        List<Format> original = List.of(av1, vp9);
        assertSame(original, VideoCodecPolicy.filterAdaptiveFormats(
                original, "PREFER_AV1", FULL_HD_60, ADAPTER));
    }

    @Test public void forceAv1KeepsOnlyAv1VideoAndNonVideoEvenWithoutAv1OrDecoder() {
        Format vp9 = new Format(VP9, 1920, 1080, 60);
        Format avc = new Format(AVC, 1280, 720, 30);
        Format audio = new Format(AUDIO, 0, 0, 0);
        List<Format> result = VideoCodecPolicy.filterAdaptiveFormats(
                List.of(vp9, avc, audio), "FORCE_AV1", null, ADAPTER);
        assertEquals(List.of(audio), result);
    }

    @Test public void forceAv1NeverFallsBackAndKeepsAv1EvenIfDecoderCannotPlayIt() {
        Format av1 = new Format(AV1, 3840, 2160, 120);
        Format vp9 = new Format(VP9, 1920, 1080, 60);
        Format audio = new Format(AUDIO, 0, 0, 0);
        List<Format> result = VideoCodecPolicy.filterAdaptiveFormats(
                List.of(av1, vp9, audio), "FORCE_AV1", (w, h, fps) -> false, ADAPTER);
        assertEquals(List.of(av1, audio), result);
    }
}