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
    private static final String AV1 = "video/av01";
    private static final String AV1_MP4 = "video/mp4; codecs=\"av01.0.05M.08\"";
    private static final String VP9 = "video/webm; codecs=\"vp09.00.51.08\"";
    private static final String AVC = "video/mp4; codecs=\"avc1.640028\"";
    private static final String AUDIO = "audio/mp4; codecs=\"mp4a.40.2\"";

    private static final class TestFormat implements VideoFormat.FormatInterface {
        private final String mimeType;
        private final int width;
        private final int height;
        private final int itag;

        TestFormat(String mimeType, int width, int height, int itag) {
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
            this.itag = itag;
        }

        @Override public String patch_getMimeType() { return mimeType; }
        @Override public int patch_getWidth() { return width; }
        @Override public int patch_getHeight() { return height; }
        @Override public int patch_getItag() { return itag; }
    }

    @Test
    public void preferAv1KeepsFallbackCodecsAvailableAtCapabilityLayer() {
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", AV1, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", AVC, AV1));
        assertTrue(VideoCodecPolicy.allowVP9("PREFER_AV1"));
    }

    @Test
    public void preferAv1FiltersRealVideoFormatsButPreservesAudioAndAllAv1Qualities() {
        TestFormat h264 = new TestFormat(AVC, 1920, 1080, 137);
        TestFormat audio = new TestFormat(AUDIO, 0, 0, 140);
        TestFormat av1_1080 = new TestFormat(AV1_MP4, 1920, 1080, 399);
        TestFormat av1_720 = new TestFormat("video/webm; codecs=\"av01.0.08M.08\"", 1280, 720, 398);
        TestFormat av1_itag_only = new TestFormat("video/mp4", 1280, 720, 394);
        TestFormat vp9 = new TestFormat(VP9, 1920, 1080, 248);

        List<VideoFormat.FormatInterface> input = List.of(h264, audio, av1_1080, av1_720, av1_itag_only, vp9);
        List<VideoFormat.FormatInterface> filtered =
                VideoCodecPolicy.filterAdaptiveFormats("PREFER_AV1", input, true);

        assertEquals(4, filtered.size());
        assertSame(audio, filtered.get(0));
        assertSame(av1_1080, filtered.get(1));
        assertSame(av1_720, filtered.get(2));
        assertSame(av1_itag_only, filtered.get(3));
    }

    @Test
    public void preferAv1FallsBackWhenNoAv1FormatWasReturned() {
        List<VideoFormat.FormatInterface> input = List.of(
                new TestFormat(AVC, 1920, 1080, 137),
                new TestFormat(AUDIO, 0, 0, 140),
                new TestFormat(VP9, 1280, 720, 247)
        );

        assertSame(input, VideoCodecPolicy.filterAdaptiveFormats("PREFER_AV1", input, true));
    }

    @Test
    public void preferAv1FallsBackWhenNoDecoderIsAvailable() {
        List<VideoFormat.FormatInterface> input = List.of(
                new TestFormat(AVC, 1920, 1080, 137),
                new TestFormat(AV1_MP4, 1920, 1080, 399)
        );

        assertSame(input, VideoCodecPolicy.filterAdaptiveFormats("PREFER_AV1", input, false));
    }

    @Test
    public void forceAv1RejectsEveryOtherCodecAndPreservesAudio() {
        TestFormat av1 = new TestFormat(AV1_MP4, 1920, 1080, 399);
        TestFormat audio = new TestFormat(AUDIO, 0, 0, 140);
        List<VideoFormat.FormatInterface> input = List.of(
                new TestFormat(AVC, 1920, 1080, 137), audio,
                new TestFormat(VP9, 1920, 1080, 248), av1
        );

        List<VideoFormat.FormatInterface> filtered =
                VideoCodecPolicy.filterAdaptiveFormats("FORCE_AV1", input, false);

        assertEquals(2, filtered.size());
        assertSame(audio, filtered.get(0));
        assertSame(av1, filtered.get(1));
        assertTrue(VideoCodecPolicy.allowCodec("FORCE_AV1", AV1_MP4, AV1));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", VP9, AV1));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", AVC, AV1));
        assertFalse(VideoCodecPolicy.allowVP9("FORCE_AV1"));
    }

    @Test
    public void forceAv1WithNoAv1VideoLeavesOnlyAudio() {
        TestFormat audio = new TestFormat(AUDIO, 0, 0, 140);
        List<VideoFormat.FormatInterface> input = List.of(
                new TestFormat(AVC, 1920, 1080, 137),
                new TestFormat(VP9, 1280, 720, 247),
                audio
        );

        List<VideoFormat.FormatInterface> filtered =
                VideoCodecPolicy.filterAdaptiveFormats("FORCE_AV1", input, false);

        assertEquals(1, filtered.size());
        assertSame(audio, filtered.get(0));
    }

    @Test
    public void defaultAllowsAllCodecsAndLeavesFormatListUntouched() {
        List<VideoFormat.FormatInterface> input = List.of(
                new TestFormat(AV1_MP4, 1920, 1080, 399),
                new TestFormat(VP9, 1920, 1080, 248),
                new TestFormat(AVC, 1920, 1080, 137)
        );
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", AV1, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", AVC, AV1));
        assertSame(input, VideoCodecPolicy.filterAdaptiveFormats("DEFAULT", input, true));
    }

    @Test
    public void vp9ModeRejectsAv1ButAllowsVp9AndAvc() {
        assertFalse(VideoCodecPolicy.allowCodec("VP9", AV1_MP4, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("VP9", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("VP9", AVC, AV1));
        assertTrue(VideoCodecPolicy.allowVP9("VP9"));
    }

    @Test
    public void mimeClassificationSeparatesMuxedVideoFromAudioOnly() {
        assertTrue(VideoCodecPolicy.isAv1Mime(AV1_MP4));
        assertTrue(VideoCodecPolicy.isAv1Itag(394));
        assertTrue(VideoCodecPolicy.isAv1Itag(571));
        assertTrue(VideoCodecPolicy.isAv1Itag(701));
        assertFalse(VideoCodecPolicy.isAv1Itag(248));
        assertTrue(VideoCodecPolicy.isCompetingVideoMime(AVC));
        assertTrue(VideoCodecPolicy.isVideoMime("video/mp4; codecs=\"avc1.640028, mp4a.40.2\""));
        assertFalse(VideoCodecPolicy.isAudioMime("video/mp4; codecs=\"avc1.640028, mp4a.40.2\""));
        assertTrue(VideoCodecPolicy.isAudioMime(AUDIO));
    }
}
