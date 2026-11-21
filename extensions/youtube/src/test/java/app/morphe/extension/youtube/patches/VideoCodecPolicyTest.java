/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class VideoCodecPolicyTest {

    private static final String AV1 = "video/av01";
    private static final String AV1_DASH = "video/mp4; codecs=\"av01.0.08M.08\"";
    private static final String VP9 = "video/x-vnd.on2.vp9";
    private static final String VP9_DASH = "video/webm; codecs=\"vp9\"";
    private static final String AVC = "video/avc";
    private static final String AVC_DASH = "video/mp4; codecs=\"avc1.640028\"";
    private static final String AUDIO_AAC = "audio/mp4; codecs=\"mp4a.40.2\"";
    private static final String AUDIO_OPUS = "audio/webm; codecs=\"opus\"";

    private static final class TestFormat {
        final String mimeType;
        final int width;
        final int height;

        TestFormat(String mimeType, int width, int height) {
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }

        String mimeType() { return mimeType; }
        int width() { return width; }
        int height() { return height; }
    }

    private static List<TestFormat> filter(String codec,
                                           List<TestFormat> formats,
                                           boolean hasDecoder,
                                           VideoCodecPolicy.Av1SizeSupport sizeSupport) {
        return VideoCodecPolicy.filterAdaptiveFormats(
                codec,
                formats,
                TestFormat::mimeType,
                TestFormat::width,
                TestFormat::height,
                hasDecoder,
                sizeSupport
        );
    }

    @Test
    public void allowCodecNeverBlocksAudioOrNull() {
        for (String mode : List.of("DEFAULT", "PREFER_AV1", "FORCE_AV1", "VP9", "AVC")) {
            assertTrue(VideoCodecPolicy.allowCodec(mode, AUDIO_AAC));
            assertTrue(VideoCodecPolicy.allowCodec(mode, AUDIO_OPUS));
            assertTrue(VideoCodecPolicy.allowCodec(mode, null));
        }
    }

    @Test
    public void allowCodecAndVp9FlagsPerMode() {
        assertTrue(VideoCodecPolicy.allowVP9("DEFAULT"));
        assertTrue(VideoCodecPolicy.allowVP9("PREFER_AV1"));
        assertTrue(VideoCodecPolicy.allowVP9("VP9"));
        assertFalse(VideoCodecPolicy.allowVP9("AVC"));
        assertFalse(VideoCodecPolicy.allowVP9("FORCE_AV1"));

        assertTrue(VideoCodecPolicy.allowCodec("FORCE_AV1", AV1));
        assertTrue(VideoCodecPolicy.allowCodec("FORCE_AV1", AV1_DASH));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", VP9));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", AVC));

        assertFalse(VideoCodecPolicy.allowCodec("VP9", AV1));
        assertTrue(VideoCodecPolicy.allowCodec("VP9", VP9));
        assertTrue(VideoCodecPolicy.allowCodec("VP9", AVC));

        assertFalse(VideoCodecPolicy.allowCodec("AVC", AV1));
        assertFalse(VideoCodecPolicy.allowCodec("AVC", VP9));
        assertTrue(VideoCodecPolicy.allowCodec("AVC", AVC));
    }

    @Test
    public void preferAv1StripsOtherVideoCodecsWhenPlayableAv1Exists() {
        TestFormat av1_1080 = new TestFormat(AV1_DASH, 1920, 1080);
        TestFormat vp9_1080 = new TestFormat(VP9_DASH, 1920, 1080);
        TestFormat avc_1080 = new TestFormat(AVC_DASH, 1920, 1080);
        TestFormat audio = new TestFormat(AUDIO_OPUS, 0, 0);

        List<TestFormat> result = filter(
                "PREFER_AV1",
                List.of(av1_1080, vp9_1080, avc_1080, audio),
                true,
                (w, h) -> true
        );

        assertEquals(List.of(av1_1080, audio), result);
    }

    @Test
    public void preferAv1FallsBackWhenAv1IsMissing() {
        TestFormat vp9 = new TestFormat(VP9_DASH, 1920, 1080);
        TestFormat avc = new TestFormat(AVC_DASH, 1920, 1080);
        TestFormat audio = new TestFormat(AUDIO_AAC, 0, 0);
        List<TestFormat> input = List.of(vp9, avc, audio);

        List<TestFormat> result = filter("PREFER_AV1", input, true, (w, h) -> true);
        assertEquals(input, result);
    }

    @Test
    public void preferAv1FallsBackWhenNoAv1DecoderOrResolutionUnsupported() {
        TestFormat av1_4k = new TestFormat(AV1_DASH, 3840, 2160);
        TestFormat vp9_1080 = new TestFormat(VP9_DASH, 1920, 1080);
        TestFormat audio = new TestFormat(AUDIO_OPUS, 0, 0);
        List<TestFormat> input = List.of(av1_4k, vp9_1080, audio);

        // No AV1 decoder on device -> keep VP9 fallback.
        assertEquals(input, filter("PREFER_AV1", input, false, (w, h) -> true));

        // AV1 decoder only supports up to 1080p -> 4K AV1 is unplayable, keep VP9 fallback.
        assertEquals(input, filter("PREFER_AV1", input, true, (w, h) -> w <= 1920 && h <= 1080));
    }

    @Test
    public void preferAv1KeepsOnlySupportedAv1Resolutions() {
        TestFormat av1_4k = new TestFormat(AV1_DASH, 3840, 2160);
        TestFormat av1_1080 = new TestFormat(AV1_DASH, 1920, 1080);
        TestFormat vp9_1080 = new TestFormat(VP9_DASH, 1920, 1080);
        TestFormat audio = new TestFormat(AUDIO_OPUS, 0, 0);

        List<TestFormat> result = filter(
                "PREFER_AV1",
                List.of(av1_4k, av1_1080, vp9_1080, audio),
                true,
                (w, h) -> w <= 1920 && h <= 1080
        );

        assertEquals(List.of(av1_1080, audio), result);
    }

    @Test
    public void forceAv1StrictlyStripsNonAv1VideoEvenWhenAv1IsMissing() {
        TestFormat vp9 = new TestFormat(VP9_DASH, 1920, 1080);
        TestFormat avc = new TestFormat(AVC_DASH, 1920, 1080);
        TestFormat audio = new TestFormat(AUDIO_AAC, 0, 0);

        List<TestFormat> result = filter("FORCE_AV1", List.of(vp9, avc, audio), false, (w, h) -> false);
        assertEquals(List.of(audio), result);
    }
}