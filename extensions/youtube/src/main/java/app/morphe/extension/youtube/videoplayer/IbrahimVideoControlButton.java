package app.morphe.extension.youtube.videoplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import app.morphe.extension.shared.spoof.SpoofVideoStreamsPatch;
import app.morphe.extension.shared.spoof.requests.StreamingDataRequest;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.patches.VideoInformation.VideoQualityInterface;
import app.morphe.extension.youtube.settings.Settings;

/** In-player entry point for Ibrahim's unified video buffer controls. */
@SuppressWarnings("unused")
public final class IbrahimVideoControlButton {
    private static volatile VideoQualityInterface qualityBeforeBackground;
    private static volatile String backgroundVideoId;

    private IbrahimVideoControlButton() {}

    /** Called from the app's screen-off receiver on the main thread. */
    public static void onScreenOff() {
        if (!Settings.IBRAHIM_BACKGROUND_144P.get() || backgroundVideoId != null) {
            return;
        }
        final String videoId = VideoInformation.getStartingOrPlayingVideoId();
        final VideoQualityInterface[] qualities = VideoInformation.getCurrentQualities();
        if (videoId == null || qualities == null || qualities.length == 0) {
            return;
        }
        VideoQualityInterface lowest = null;
        for (VideoQualityInterface quality : qualities) {
            if (quality == null) continue;
            final int resolution = quality.patch_getResolution();
            if (resolution <= 0) continue; // Skip Auto and unknown entries.
            if (lowest == null || resolution < lowest.patch_getResolution()) {
                lowest = quality;
            }
        }
        if (lowest == null) {
            return;
        }
        final VideoQualityInterface current = VideoInformation.getCurrentQuality();
        if (current == null || current.patch_getResolution() <= 144) {
            return;
        }
        qualityBeforeBackground = current;
        backgroundVideoId = videoId;
        VideoInformation.changeQuality(lowest);
    }

    /** Restores the quality only when the same video is still loaded. */
    public static void onScreenOn() {
        final String savedVideoId = backgroundVideoId;
        final VideoQualityInterface previous = qualityBeforeBackground;
        backgroundVideoId = null;
        qualityBeforeBackground = null;
        if (savedVideoId == null || previous == null) {
            return;
        }
        final String currentVideoId = VideoInformation.getStartingOrPlayingVideoId();
        if (savedVideoId.equals(currentVideoId)) {
            VideoInformation.changeQuality(previous);
        }
    }

    public static void initializeButton(View sourceButton) {
        TextView button = PlayerOverlayButton.addButtonWithTextOverlay(
                sourceButton,
                () -> Settings.SPOOF_VIDEO_STREAMS.get(),
                view -> showDialog(view.getContext()),
                null
        );
        if (button != null) {
            button.setText("إب");
            button.setContentDescription("إعدادات إبراهيم للفيديو");
            showWelcome(contextFor(button.getContext()));
        }
    }

    private static Activity contextFor(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper && !(current instanceof Activity)) {
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static void showWelcome(Activity activity) {
        if (activity == null || Settings.IBRAHIM_WELCOME_SHOWN.get()) {
            return;
        }
        Settings.IBRAHIM_WELCOME_SHOWN.save(true);
        activity.getWindow().getDecorView().post(() -> {
            if (activity.isFinishing()) {
                return;
            }
            new AlertDialog.Builder(activity)
                    .setTitle("مرحبًا إبراهيم")
                    .setMessage("تم تجهيز مشغل الفيديو الخاص بك.\n\nإعداد وتطوير: إبراهيم")
                    .setPositiveButton("ابدأ", null)
                    .show();
        });
    }

    private static void showDialog(Context context) {
        final int[] pending = {
                Settings.SABR_VIDEO_READAHEAD_TENTHS.get(),
                Settings.SABR_VIDEO_READAHEAD_LOW_TENTHS.get(),
                Settings.SABR_VIDEO_READAHEAD_RESUME_TENTHS.get(),
                Settings.SABR_VIDEO_READAHEAD_STABLE_SECONDS.get()
        };

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(context, 22);
        content.setPadding(horizontal, dp(context, 8), horizontal, dp(context, 4));
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        scrollView.addView(content);

        String videoId = VideoInformation.getStartingOrPlayingVideoId();
        boolean sabrActive = videoId != null && !videoId.isEmpty()
                && StreamingDataRequest.playerClientUsesSABR(videoId);

        CheckBox background144p = new CheckBox(context);
        background144p.setText("خفض الجودة تلقائيًا إلى 144p عند إطفاء الشاشة");
        background144p.setChecked(Settings.IBRAHIM_BACKGROUND_144P.get());
        content.addView(background144p);

        TextView status = new TextView(context);
        boolean exoPlayerActive = VideoInformation.isExoPlayerInitialized();
        String engineStatus = sabrActive
                ? "المسار الحالي يستخدم SABR — التحكم في بفر الفيديو نشط."
                : exoPlayerActive
                        ? "المسار الحالي يستخدم ExoPlayer — التحكم في بفر الفيديو نشط."
                        : "لم يتم التعرف على محرك البث الحالي؛ إعدادات البفر محفوظة لكن تطبيقها غير مؤكد.";
        if (sabrActive) {
            String mimeType = SpoofVideoStreamsPatch.getSabrVideoMimeType();
            long receivedBytes = SpoofVideoStreamsPatch.getSabrReceivedBytes();
            engineStatus += "\\n\\nترميز الفيديو: " + (mimeType == null ? "لم يُحدد بعد" : mimeType)
                    + "\\nبيانات SABR المستلمة: " + formatDataSize(receivedBytes)
                    + " (تشمل ترويسات النقل).";
        } else if (exoPlayerActive) {
            engineStatus += "\\n\\nتفاصيل الترميز وحجم البيانات غير متاحة من واجهة ExoPlayer الحالية.";
        }
        status.setText(engineStatus);
        status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(context, 12));
        content.addView(status);

        addSlider(context, content, "البفر المستقر", pending, 0, 5, 300, 1, true);
        addSlider(context, content, "البفر المنخفض أثناء التقديم والإرجاع", pending, 1, 5, 100, 1, true);
        addSlider(context, content, "حد استئناف التحميل", pending, 2, 1, 100, 1, true);
        addSlider(context, content, "مدة الاستقرار", pending, 3, 5, 300, 5, false);

        new AlertDialog.Builder(context)
                .setTitle("إعدادات إبراهيم للفيديو")
                .setView(scrollView)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    Settings.SABR_VIDEO_READAHEAD_TENTHS.save(pending[0]);
                    Settings.SABR_VIDEO_READAHEAD_LOW_TENTHS.save(pending[1]);
                    Settings.SABR_VIDEO_READAHEAD_RESUME_TENTHS.save(pending[2]);
                    Settings.SABR_VIDEO_READAHEAD_STABLE_SECONDS.save(pending[3]);
                    Settings.IBRAHIM_BACKGROUND_144P.save(background144p.isChecked());
                })
                .show();
    }

    private static String formatDataSize(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }

    private static void addSlider(Context context, LinearLayout parent, String title,
                                  int[] pending, int index, int min, int max, int step,
                                  boolean tenthsOfSecond) {
        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setPadding(0, dp(context, 8), 0, dp(context, 2));
        parent.addView(titleView);

        TextView valueView = new TextView(context);
        valueView.setText(formatValue(pending[index], tenthsOfSecond));
        valueView.setTextSize(13);
        parent.addView(valueView);

        SeekBar seekBar = new SeekBar(context);
        seekBar.setMax((max - min) / step);
        seekBar.setProgress((Math.max(min, Math.min(max, pending[index])) - min) / step);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                pending[index] = min + progress * step;
                valueView.setText(formatValue(pending[index], tenthsOfSecond));
            }

            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        parent.addView(seekBar);
    }

    private static String formatValue(int value, boolean tenthsOfSecond) {
        return tenthsOfSecond
                ? String.format(Locale.ROOT, "%.1f ثانية", value / 10.0)
                : value + " ثانية";
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
