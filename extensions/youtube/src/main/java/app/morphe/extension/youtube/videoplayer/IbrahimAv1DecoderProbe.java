/*
 * Copyright 2026.
 * Ibrahim AV1 decoder preflight diagnostics.
 *
 * This probes Android MediaCodec decoder creation/configuration/startup.
 * It deliberately does not report a successful frame decode: a real AV1
 * access unit must be supplied to prove that the decoder can decode frames.
 */
package app.morphe.extension.youtube.videoplayer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime preflight for the AV1 decoder candidates on this Android device. */
public final class IbrahimAv1DecoderProbe {
    private static final String MIME_AV1 = "video/av01";
    private static final String[] PREFERRED_NAMES = {
            "c2.android.av1-dav1d.decoder",
            "c2.android.av1.decoder",
            "c2.qti.av1.decoder"
    };

    private IbrahimAv1DecoderProbe() {}

    public static void run(Context context) {
        final Context app = context.getApplicationContext();
        final AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle("اختبار مفككات AV1")
                .setMessage("جاري اختبار إنشاء وتهيئة وتشغيل المفككات...")
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
                        .setTitle("نتيجة اختبار مفككات AV1")
                        .setView(scroll)
                        .setPositiveButton("نسخ التقرير", (dialog, which) -> {
                            ClipboardManager clipboard = (ClipboardManager)
                                    app.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("AV1 decoder preflight", report));
                            }
                        })
                        .setNegativeButton("إغلاق", null)
                        .show();
            });
        }, "Ibrahim-AV1-Probe").start();
    }

    private static String runTests() {
        StringBuilder out = new StringBuilder(4096);
        out.append("IBRAHIM AV1 DECODER PREFLIGHT v1\n");
        out.append("Android SDK: ").append(Build.VERSION.SDK_INT).append("\n");
        out.append("MIME: ").append(MIME_AV1).append("\n");
        out.append("Test format: 640x360, 30fps\n");
        out.append("NOTE: no AV1 bitstream is queued; DECODE_FRAME is intentionally NOT_TESTED.\n\n");

        Map<String, MediaCodecInfo> available = new LinkedHashMap<>();
        try {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos()) {
                if (info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (MIME_AV1.equalsIgnoreCase(type)) {
                        available.put(info.getName(), info);
                        break;
                    }
                }
            }
        } catch (Exception error) {
            out.append("INVENTORY=ERROR ").append(error.getClass().getSimpleName())
                    .append(": ").append(error.getMessage()).append("\n");
            return out.toString();
        }

        if (available.isEmpty()) {
            out.append("RESULT=NO_AV1_DECODER_ADVERTISED\n");
            return out.toString();
        }

        out.append("[ADVERTISED DECODERS]\n");
        for (Map.Entry<String, MediaCodecInfo> entry : available.entrySet()) {
            MediaCodecInfo info = entry.getValue();
            out.append(entry.getKey());
            if (Build.VERSION.SDK_INT >= 29) {
                out.append(" | ").append(info.isHardwareAccelerated() ? "HW" : "SW")
                        .append(" | vendor=").append(info.isVendor());
            }
            out.append("\n");
        }

        List<String> ordered = new ArrayList<>();
        for (String preferred : PREFERRED_NAMES) {
            if (available.containsKey(preferred)) ordered.add(preferred);
        }
        for (String name : available.keySet()) {
            if (!ordered.contains(name)) ordered.add(name);
        }

        out.append("\n[ORDERED RUNTIME TESTS]\n");
        boolean lifecycleSuccess = false;
        for (String name : ordered) {
            out.append("\nDECODER=").append(name).append("\n");
            MediaCodec codec = null;
            boolean created = false;
            boolean configured = false;
            boolean started = false;
            try {
                codec = MediaCodec.createByCodecName(name);
                created = true;
                out.append("CREATE_BY_NAME=PASS\n");

                MediaFormat format = MediaFormat.createVideoFormat(MIME_AV1, 640, 360);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 512 * 1024);
                codec.configure(format, null, null, 0);
                configured = true;
                out.append("CONFIGURE=PASS\n");

                codec.start();
                started = true;
                lifecycleSuccess = true;
                out.append("START=PASS\n");
                out.append("DECODE_FRAME=NOT_TESTED_NO_AV1_ACCESS_UNIT\n");
            } catch (Throwable error) {
                out.append("CREATE_BY_NAME=").append(created ? "PASS" : "FAIL").append("\n");
                out.append("CONFIGURE=").append(configured ? "PASS" : "FAIL").append("\n");
                out.append("START=").append(started ? "PASS" : "FAIL").append("\n");
                out.append("ERROR=").append(error.getClass().getName())
                        .append(": ").append(String.valueOf(error.getMessage())).append("\n");
            } finally {
                if (codec != null) {
                    try {
                        if (started) codec.stop();
                    } catch (Exception ignored) {}
                    try {
                        codec.release();
                    } catch (Exception ignored) {}
                }
            }
        }

        out.append("\n[SUMMARY]\n");
        out.append("LIFECYCLE_PREFLIGHT=").append(lifecycleSuccess ? "AT_LEAST_ONE_PASS" : "ALL_FAILED").append("\n");
        out.append("DECODE_FRAME=NOT_TESTED\n");
        out.append("Meaning: PASS proves create/configure/start only; it does not prove AV1 frame decoding or playback performance.\n");
        return out.toString();
    }
}
