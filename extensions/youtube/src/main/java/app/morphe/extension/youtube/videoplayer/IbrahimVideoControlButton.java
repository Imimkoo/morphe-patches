package app.morphe.extension.youtube.videoplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import app.morphe.extension.shared.spoof.requests.StreamingDataRequest;
import app.morphe.extension.youtube.patches.VideoInformation;
import app.morphe.extension.youtube.settings.Settings;

/** In-player entry point for Ibrahim's SABR video buffer controls. */
@SuppressWarnings("unused")
public final class IbrahimVideoControlButton {
    private IbrahimVideoControlButton() {}

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
        }
    }

    private static void showDialog(Context context) {
        final int[] pending = {
                Settings.SABR_VIDEO_READAHEAD_TENTHS.get(),
                Settings.SABR_VIDEO_READAHEAD_LOW_TENTHS.get(),
                Settings.SABR_VIDEO_READAHEAD_STABLE_SECONDS.get()
        };

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(context, 22);
        content.setPadding(horizontal, dp(context, 8), horizontal, dp(context, 4));

        String videoId = VideoInformation.getStartingOrPlayingVideoId();
        boolean sabrActive = videoId != null && !videoId.isEmpty()
                && StreamingDataRequest.playerClientUsesSABR(videoId);

        TextView status = new TextView(context);
        status.setText(sabrActive
                ? "المسار الحالي يستخدم SABR — التحكم في بفر الفيديو نشط."
                : "المسار الحالي ليس SABR. إعدادات البفر هنا محفوظة، لكنها لن تتحكم في هذا المسار حتى يكتمل ربطه.");
        status.setTextSize(13);
        status.setPadding(0, 0, 0, dp(context, 12));
        content.addView(status);

        addSlider(context, content, "البفر المستقر", pending, 0, 5, 300, 1, true);
        addSlider(context, content, "البفر المنخفض أثناء التقديم والإرجاع", pending, 1, 5, 100, 1, true);
        addSlider(context, content, "مدة الاستقرار", pending, 2, 5, 300, 5, false);

        new AlertDialog.Builder(context)
                .setTitle("إعدادات إبراهيم للفيديو")
                .setView(content)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    Settings.SABR_VIDEO_READAHEAD_TENTHS.save(pending[0]);
                    Settings.SABR_VIDEO_READAHEAD_LOW_TENTHS.save(pending[1]);
                    Settings.SABR_VIDEO_READAHEAD_STABLE_SECONDS.save(pending[2]);
                })
                .show();
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
