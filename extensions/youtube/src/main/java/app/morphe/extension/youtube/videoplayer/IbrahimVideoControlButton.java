/*
 * Copyright 2026.
 * Ibrahim Universal Video Control - independent implementation.
 */
package app.morphe.extension.youtube.videoplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.youtube.patches.VideoInformation;

/**
 * Independent codec and load-control policy. Does not delegate to the upstream
 * DisableVideoCodecsPatch or PlaybackBufferPatch implementations.
 */
@SuppressWarnings("unused")
public final class IbrahimVideoControlButton {
    private static final String PREFS = "ibrahim_universal_video_control";
    private static final String MODE_KEY = "codec_mode";
    private static final String BUFFER_ENABLED_KEY = "buffer_enabled";
    private static final String BUFFER_TARGET_KEY = "buffer_target_seconds";
    private static final String BUFFER_RESUME_KEY = "buffer_resume_seconds";
    private static final String AUDIO_ONLY_KEY = "audio_only";

    public static final int DEFAULT = 0;
    public static final int PREFER_AV1 = 1;
    public static final int FORCE_AV1_ONLY = 2;

    private static volatile Context appContext;
    private static volatile boolean bufferThrottled;

    private IbrahimVideoControlButton() {}

    /** Injection point called from the player overlay controls row. */
    public static void initializeButton(View controlsView) {
        try {
            appContext = controlsView.getContext().getApplicationContext();
            TextView button = PlayerOverlayButton.addButtonWithTextOverlay(
                    controlsView,
                    view -> showPanel(view.getContext()),
                    null
            );
            if (button != null) button.setText("إبراهيم");
        } catch (Exception ex) {
            Logger.printException(() -> "Ibrahim button initialization failed", ex);
        }
    }

    private static int getMode(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(MODE_KEY, PREFER_AV1);
    }

    private static Context getContext() {\n        Context context = appContext;\n        return context != null ? context : Utils.getContext();\n    }\n\n    private static boolean isAudioOnly(Context context) {\n        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUDIO_ONLY_KEY, false);\n    }\n\n    public static ClientType getPreferredAv1Client(ClientType requested) {\n        Context context = getContext();\n        if (context == null || getMode(context) == DEFAULT) return requested;\n        if (requested == ClientType.ANDROID_VR_SABR) return ClientType.ANDROID_XR_SABR;\n        if (requested == ClientType.ANDROID_VR_DASH) return ClientType.ANDROID_XR_DASH;\n        if (requested == ClientType.ANDROID_VR_EXPERIMENTAL) return ClientType.ANDROID_XR_EXPERIMENTAL;\n        if (requested == ClientType.VISIONOS_1_02) return ClientType.VISIONOS_1_03;\n        if (requested == ClientType.VISIONOS_1_06) return ClientType.VISIONOS_1_06_AV1;\n        return ClientType.TV_SIMPLY;\n    }\n\n    private static boolean isBufferEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(BUFFER_ENABLED_KEY, true);
    }

    private static int getTargetSeconds(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(BUFFER_TARGET_KEY, 3);
    }

    private static int getResumeSeconds(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(BUFFER_RESUME_KEY, 2);
    }

    private static void showPanel(Context context) {
        try {
            final LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (20 * context.getResources().getDisplayMetrics().density);
            content.setPadding(pad, pad / 2, pad, pad / 2);

            TextView codecTitle = new TextView(context);
            codecTitle.setText("سياسة ترميز الفيديو");
            content.addView(codecTitle);

            RadioGroup codecGroup = new RadioGroup(context);
            codecGroup.setOrientation(RadioGroup.VERTICAL);
            String[] labels = {
                    "Default — اختيار YouTube الأصلي",
                    "Prefer AV1 — تعطيل VP9 مع السماح بباقي الترميزات",
                    "Force AV1 only — منع كل ترميز فيديو غير AV1"
            };
            int selectedMode = getMode(context);
            for (int i = 0; i < labels.length; i++) {
                RadioButton option = new RadioButton(context);
                option.setId(View.generateViewId());
                option.setText(labels[i]);
                option.setTag(i);
                codecGroup.addView(option);
                if (i == selectedMode) option.setChecked(true);
            }
            content.addView(codecGroup);

            TextView bufferTitle = new TextView(context);
            bufferTitle.setText("\nالتحكم في التحميل المسبق");
            content.addView(bufferTitle);

            CheckBox audioOnly = new CheckBox(context);\n            audioOnly.setText("تشغيل الفيديوهات كصوت فقط");\n            audioOnly.setChecked(isAudioOnly(context));\n            content.addView(audioOnly);\n\n            CheckBox bufferEnabled = new CheckBox(context);
            bufferEnabled.setText("تفعيل حد التخزين المؤقت");
            bufferEnabled.setChecked(isBufferEnabled(context));
            content.addView(bufferEnabled);

            final TextView targetLabel = new TextView(context);
            final SeekBar targetBar = new SeekBar(context);
            targetBar.setMax(9);
            targetBar.setProgress(Math.max(0, Math.min(9, getTargetSeconds(context) - 1)));
            targetLabel.setText("Target: " + (targetBar.getProgress() + 1) + " ثانية");
            content.addView(targetLabel);
            content.addView(targetBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            final TextView resumeLabel = new TextView(context);
            final SeekBar resumeBar = new SeekBar(context);
            resumeBar.setMax(9);
            resumeBar.setMax(Math.max(0, targetBar.getProgress()));
            resumeBar.setProgress(Math.max(0, Math.min(resumeBar.getMax(), getResumeSeconds(context))));
            resumeLabel.setText("Resume: " + resumeBar.getProgress() + " ثانية");
            content.addView(resumeLabel);
            content.addView(resumeBar, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            targetBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int target = progress + 1;
                    targetLabel.setText("Target: " + target + " ثانية");
                    resumeBar.setMax(Math.max(0, target - 1));
                    if (resumeBar.getProgress() >= target) resumeBar.setProgress(target - 1);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            resumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    resumeLabel.setText("Resume: " + progress + " ثانية");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            TextView liveInfo = new TextView(context);
            liveInfo.setText("\n" + currentVideoSummary()
                    + "\nالتغييرات تطبق أثناء قرارات التحميل؛ سياسة الترميز قد تتطلب إعادة تحميل الفيديو.");
            content.addView(liveInfo);

            new AlertDialog.Builder(context)
                    .setTitle("إبراهيم · التحكم في الفيديو")
                    .setView(content)
                    .setPositiveButton("حفظ", (dialog, which) -> {
                        int checkedId = codecGroup.getCheckedRadioButtonId();
                        int mode = DEFAULT;
                        for (int i = 0; i < codecGroup.getChildCount(); i++) {
                            RadioButton option = (RadioButton) codecGroup.getChildAt(i);
                            if (option.getId() == checkedId) {
                                mode = (int) option.getTag();
                                break;
                            }
                        }
                        int target = targetBar.getProgress() + 1;
                        int resume = Math.min(resumeBar.getProgress(), target - 1);
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                                .putInt(MODE_KEY, mode)
                                .putBoolean(AUDIO_ONLY_KEY, audioOnly.isChecked())\n                                .putBoolean(BUFFER_ENABLED_KEY, bufferEnabled.isChecked())
                                .putInt(BUFFER_TARGET_KEY, target)
                                .putInt(BUFFER_RESUME_KEY, resume)
                                .apply();
                        bufferThrottled = false;
                    })
                    .setNegativeButton("إغلاق", null)
                    .show();
        } catch (Exception ex) {
            Logger.printException(() -> "Ibrahim panel failed", ex);
        }
    }

    private static String currentVideoSummary() {
        try {
            VideoInformation.VideoQualityInterface quality = VideoInformation.getCurrentQuality();
            if (quality == null) return "الجودة الحالية: غير متاحة";
            return "الجودة الحالية: " + quality.patch_getQualityName()
                    + " (" + quality.patch_getResolution() + "p)";
        } catch (Exception ignored) {
            return "معلومات الجودة الحالية: غير متاحة";
        }
    }

    /** Hook: controls whether YouTube may use VP9. */
    public static boolean allowVP9() {
        Context context = appContext;
        return context == null || getMode(context) == DEFAULT;
    }

    /**
     * Hook: filters video MIME types in Force AV1 only mode.
     * Audio codecs are deliberately left untouched.
     */
    public static boolean allowCodec(String mimeType) {
        Context context = appContext;
        if (context == null || getMode(context) != FORCE_AV1_ONLY) return true;
        if (mimeType == null || !mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("video/")) {
            return true;
        }
        return "video/av01".equalsIgnoreCase(mimeType);
    }

    /**
     * Load-control hook with hysteresis: stop requesting more media at Target,
     * and resume only after buffered duration falls to Resume.
     */
    public static long applyBufferPolicy(long bufferedUs) {
        Context context = appContext;
        if (context == null || !isBufferEnabled(context)) {
            bufferThrottled = false;
            return bufferedUs;
        }

        long targetUs = getTargetSeconds(context) * 1_000_000L;
        long resumeUs = getResumeSeconds(context) * 1_000_000L;
        if (bufferThrottled) {
            if (bufferedUs <= resumeUs) bufferThrottled = false;
        } else if (bufferedUs >= targetUs) {
            bufferThrottled = true;
        }

        // The patched load-control decision sees a saturated buffer while throttled.
        return bufferThrottled ? Math.max(bufferedUs, targetUs + 120_000_000L) : bufferedUs;
    }

    public static boolean isBufferThrottled() {
        return bufferThrottled;
    }
}
