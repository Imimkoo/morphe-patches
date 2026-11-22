/*
 * Copyright 2026.
 * Ibrahim embedded dav1d runtime diagnostics.
 *
 * Tests the AV1 decoder shipped inside the YouTube APK. It does not create
 * or depend on an Android MediaCodec decoder supplied by the phone.
 */
package app.morphe.extension.youtube.videoplayer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Runtime preflight for the APK-bundled Media3 dav1d decoder. */
public final class IbrahimAv1DecoderProbe {
    private IbrahimAv1DecoderProbe() {}

    public static void run(Context context) {
        final Context app = context.getApplicationContext();
        final AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle("اختبار مفكك dav1d المدمج")
                .setMessage("جاري اختبار المكتبة الموجودة داخل التطبيق...")
                .setCancelable(false)
                .create();
        progress.show();

        new Thread(() -> {
            String report = runTests();
            new Handler(Looper.getMainLooper()).post(() -> {
                progress.dismiss();
                TextView text = new TextView(context);
                int padding = (int) (16 * context.getResources().getDisplayMetrics().density);
                text.setPadding(padding, padding, padding, padding);
                text.setTextIsSelectable(true);
                text.setText(report);
                ScrollView scroll = new ScrollView(context);
                scroll.addView(text);
                new AlertDialog.Builder(context)
                        .setTitle("نتيجة اختبار dav1d المدمج")
                        .setView(scroll)
                        .setPositiveButton("نسخ التقرير", (dialog, which) -> {
                            ClipboardManager clipboard = (ClipboardManager)
                                    app.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Embedded dav1d test", report));
                            }
                        })
                        .setNegativeButton("إغلاق", null)
                        .show();
            });
        }, "Ibrahim-Embedded-dav1d-Probe").start();
    }

    private static String runTests() {
        StringBuilder out = new StringBuilder(2048);
        out.append("IBRAHIM EMBEDDED DAV1D TEST v1\n");
        out.append("Decoder source: APK-bundled libdav1dJNI.so\n");
        out.append("Android MediaCodec: NOT USED\n");

        Object decoder = null;
        try {
            Class<?> libraryClass = Class.forName("androidx.media3.decoder.av1.Dav1dLibrary");
            Method availableMethod = libraryClass.getMethod("a");
            boolean available = Boolean.TRUE.equals(availableMethod.invoke(null));
            out.append("DAV1D_LIBRARY_LOAD=").append(available ? "PASS" : "FAIL").append("\n");
            if (!available) {
                out.append("DECODER_INIT=NOT_RUN_LIBRARY_UNAVAILABLE\n");
                return out.toString();
            }

            Class<?> decoderClass = Class.forName("androidx.media3.decoder.av1.Dav1dDecoder");
            Constructor<?> constructor = decoderClass.getConstructor(
                    int.class, int.class, int.class, int.class, int.class, boolean.class);

            // 2 input buffers, 2 output buffers, 64 KiB initial input buffer,
            // 2 dav1d worker threads, 2-frame delay, default allocator.
            decoder = constructor.newInstance(2, 2, 65536, 2, 2, false);
            out.append("DECODER_INIT=PASS\n");
            Object name = decoderClass.getMethod("getName").invoke(decoder);
            out.append("DECODER_NAME=").append(String.valueOf(name)).append("\n");

            decoderClass.getMethod("release").invoke(decoder);
            decoder = null;
            out.append("DECODER_RELEASE=PASS\n");
            out.append("DECODE_FRAME=NOT_TESTED_NO_AV1_ACCESS_UNIT\n");
            out.append("Meaning: native dav1d initialized successfully inside the APK; frame decode and playback still require a real stream test.\n");
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            out.append("RESULT=FAIL\n");
            out.append("ERROR=").append(cause.getClass().getName())
                    .append(": ").append(String.valueOf(cause.getMessage())).append("\n");
        } finally {
            if (decoder != null) {
                try {
                    decoder.getClass().getMethod("release").invoke(decoder);
                } catch (Throwable ignored) {}
            }
        }
        return out.toString();
    }
}
