/*
 * Copyright 2026 Morphe.
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.extension.youtube.patches;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VideoCodecPolicyTest {
    private static final String AV1 = "video/av01";
    private static final String VP9 = "video/x-vnd.on2.vp9";
    private static final String AVC = "video/avc";

    @Test
    public void preferAv1KeepsFallbackCodecsAvailable() {
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", AV1, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("PREFER_AV1", AVC, AV1));
        assertTrue(VideoCodecPolicy.allowVP9("PREFER_AV1"));
    }

    @Test
    public void forceAv1RejectsEveryOtherCodec() {
        assertTrue(VideoCodecPolicy.allowCodec("FORCE_AV1", AV1, AV1));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", VP9, AV1));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", AVC, AV1));
        assertFalse(VideoCodecPolicy.allowVP9("FORCE_AV1"));
    }

    @Test
    public void forceAv1HasNoCodecFallbackWhenAv1IsMissing() {
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", VP9, AV1));
        assertFalse(VideoCodecPolicy.allowCodec("FORCE_AV1", AVC, AV1));
    }

    @Test
    public void defaultAllowsAllCodecs() {
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", AV1, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("DEFAULT", AVC, AV1));
        assertTrue(VideoCodecPolicy.allowVP9("DEFAULT"));
    }

    @Test
    public void vp9ModeRejectsAv1ButAllowsVp9AndAvc() {
        assertFalse(VideoCodecPolicy.allowCodec("VP9", AV1, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("VP9", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("VP9", AVC, AV1));
        assertTrue(VideoCodecPolicy.allowVP9("VP9"));
    }

    @Test
    public void avcModeRejectsAv1AndVp9() {
        assertFalse(VideoCodecPolicy.allowCodec("AVC", AV1, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("AVC", VP9, AV1));
        assertTrue(VideoCodecPolicy.allowCodec("AVC", AVC, AV1));
        assertFalse(VideoCodecPolicy.allowVP9("AVC"));
    }
}
