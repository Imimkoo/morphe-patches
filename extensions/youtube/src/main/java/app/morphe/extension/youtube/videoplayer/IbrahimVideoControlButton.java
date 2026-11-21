/*
 * Copyright 2026.
 * Ibrahim Universal Video Control - independent implementation.
 */
package app.morphe.extension.youtube.videoplayer;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.patches.VideoInformation;

/**
 * Independent codec policy control. This class deliberately does not delegate to
 * DisableVideoCodecsPatch or PlaybackBufferPatch.
 */
@SuppressWarnings("unused")
public final class IbrahimVideoControlButton {
    private static final String PREFS = "ibrahim_universal_video_control";
    private static final String MODE_KEY = "codec_mode";
    public static final int DEFAULT = 0;
    public static final int PREFER_AV1 = 1;
    public static final int FORCE_AV1_ONLY = 2;
    private static volatile Context appContext;

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
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(MODE_KEY, DEFAULT);
    }

    private static void setMode(Context context, int mode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(MODE_KEY, mode).apply();
    }

    private static void showPanel(Context context) {
        try {
            final String[] modes = {
                    "Default — YouTube original",
                    "Prefer AV1 — block VP9 where possible",
                    "Force AV1 only — other video codecs blocked"
            };
            final int selected = getMode(context);
            final String current = currentVideoSummary();
            new AlertDialog.Builder(context)
                    .setTitle("إبراهيم · التحكم في الفيديو")
                    .setMessage(current + "\n\nتغيير سياسة الترميز قد يحتاج تشغيل الفيديو التالي أو إعادة تحميل البث.")
                    .setSingleChoiceItems(modes, selected, (dialog, which) -> {
                        setMode(context, which);
                        dialog.dismiss();
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
            if (quality == null) return "الجودة: لم تُحدد بعد";
            return "الجودة الحالية: " + quality.patch_getQualityName()
                    + " (" + quality.patch_getResolution() + "p)";
        } catch (Exception ignored) {
            return "معلومات الجودة غير متاحة حاليًا";
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
}
