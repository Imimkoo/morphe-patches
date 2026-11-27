package app.ibrahim.youtube;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Hysteresis-based forward-buffer controller.
 *
 * The injected load-control hook receives the actual buffered-ahead duration from
 * YouTube's own pipeline. While throttled, it reports a saturated duration only to
 * shouldContinueLoading(), stopping new media requests. The player, renderer,
 * selected stream and already-buffered samples remain owned by YouTube.
 */
public final class IbrahimBufferPolicy {
    static final String PREFS = "ibrahim_buffer_only";
    static final String ENABLED = "enabled";
    static final String TARGET_SECONDS = "target_seconds";
    static final String RESUME_SECONDS = "resume_seconds";

    private static volatile Context applicationContext;
    private static volatile boolean throttled;
    private static final Object STATE_LOCK = new Object();

    private IbrahimBufferPolicy() {}

    public static void setApplicationContext(Context context) {
        if (context != null) {
            applicationContext = context.getApplicationContext();
        }
    }

    private static Context getApplicationContext() {
        Context context = applicationContext;
        if (context != null) {
            return context;
        }

        // The player load-control method can run before the Ibrahim button is created.
        // Resolve the already-running Application without requiring another patch or player.
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object app = activityThread.getMethod("currentApplication").invoke(null);
            if (app instanceof Application) {
                context = ((Application) app).getApplicationContext();
                applicationContext = context;
                return context;
            }
        } catch (Throwable ignored) {
            // Leave the default player policy untouched until a Context is available.
        }
        return null;
    }

    public static int getTargetSeconds(Context context) {
        return preferences(context).getInt(TARGET_SECONDS, 3);
    }

    public static int getResumeSeconds(Context context) {
        int target = Math.max(1, Math.min(10, getTargetSeconds(context)));
        return Math.min(
                Math.max(0, target - 1),
                preferences(context).getInt(RESUME_SECONDS, 2)
        );
    }

    public static boolean isEnabled(Context context) {
        return preferences(context).getBoolean(ENABLED, true);
    }

    public static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Injected at every matched buffered-duration read in shouldContinueLoading(). */
    public static long apply(long bufferedUs) {
        Context context = getApplicationContext();
        if (context == null) {
            return bufferedUs;
        }

        SharedPreferences prefs = preferences(context);
        if (!prefs.getBoolean(ENABLED, true)) {
            throttled = false;
            return bufferedUs;
        }

        int targetSeconds = Math.max(1, Math.min(10, prefs.getInt(TARGET_SECONDS, 3)));
        int resumeSeconds = Math.max(
                0,
                Math.min(targetSeconds - 1, prefs.getInt(RESUME_SECONDS, 2))
        );
        long targetUs = targetSeconds * 1_000_000L;
        long resumeUs = resumeSeconds * 1_000_000L;

        synchronized (STATE_LOCK) {
            if (throttled) {
                if (bufferedUs <= resumeUs) {
                    throttled = false;
                }
            } else if (bufferedUs >= targetUs) {
                throttled = true;
            }
        }

        // Force the existing load-control comparison to return "do not load".
        // No stream format, codec, decoder, or player implementation is modified.
        return throttled
                ? Math.max(bufferedUs, targetUs + 120_000_000L)
                : bufferedUs;
    }

    public static boolean isThrottled() {
        return throttled;
    }

    public static void resetState() {
        synchronized (STATE_LOCK) {
            throttled = false;
        }
    }
}
