package app.morphe.extension.youtube.patches;

import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch;
import app.morphe.extension.youtube.settings.Settings;

/**
 * ExoPlayer adapter for the shared target/resume buffer policy.
 * The SABR adapter controls NextRequestPolicy; this adapter controls shouldContinueLoading.
 */
@SuppressWarnings("unused")
public final class PlaybackBufferPatch {
    private static final long FORCED_STOP_BUFFERED_US = 120_000_000L;
    private static volatile long playbackGeneration;

    private static final class ThreadState {
        long generation = -1;
        boolean holding;
    }

    private static final ThreadLocal<ThreadState> state =
            ThreadLocal.withInitial(ThreadState::new);

    private PlaybackBufferPatch() {}

    /** Reset hysteresis when YouTube starts a different video. */
    public static void onPlaybackStarted() {
        playbackGeneration++;
    }

    /**
     * Re-shapes ExoPlayer's buffered-duration input so its existing load-control decision
     * observes the same target/resume hysteresis as the SABR adapter.
     */
    public static long adjustBufferedDurationUs(long bufferedDurationUs) {
        if (!Settings.SPOOF_VIDEO_STREAMS.get()) {
            return bufferedDurationUs;
        }

        ThreadState current = state.get();
        final long generation = playbackGeneration;
        if (current.generation != generation) {
            current.generation = generation;
            current.holding = false;
        }

        final long targetUs = Math.max(500_000L,
                SpoofVideoStreamsPatch.getVideoBufferTargetMs() * 1000L);
        final long resumeUs = Math.max(100_000L,
                Math.min(SpoofVideoStreamsPatch.getVideoBufferResumeMs(targetUs / 1000L) * 1000L,
                        targetUs - 100_000L));

        if (current.holding) {
            if (bufferedDurationUs <= resumeUs) {
                current.holding = false;
            } else {
                return FORCED_STOP_BUFFERED_US;
            }
        }

        if (bufferedDurationUs >= targetUs) {
            current.holding = true;
            return FORCED_STOP_BUFFERED_US;
        }

        // Below the target, make the original load-control logic see a low buffer and continue
        // loading, while preserving its independent byte-size safety checks.
        return 0L;
    }
}
