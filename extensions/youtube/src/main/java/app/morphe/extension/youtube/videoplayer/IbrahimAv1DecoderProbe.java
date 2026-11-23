/*
 * Copyright 2026.
 * Ibrahim embedded dav1d runtime diagnostics.
 *
 * Decodes a tiny generated AV1 OBU frame using the native dav1d library
 * bundled inside the YouTube APK. Android MediaCodec is never used.
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Base64;

/** Runtime test for the APK-bundled Media3 dav1d decoder, including a real AV1 frame. */
public final class IbrahimAv1DecoderProbe {
    private static final String TEST_FRAME_BASE64 =
            "EgAKCgAAAAKv/5tfIAgyExAA0AAAAoAAAAoFgGGyyWbWcUo=";

    private IbrahimAv1DecoderProbe() {}

    public static void run(Context context) {
        final Context app = context.getApplicationContext();
        final AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle("اختبار مفكك dav1d المدمج")
                .setMessage("جاري تحميل المكتبة وفك إطار AV1 تجريبي...")
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
        out.append("IBRAHIM EMBEDDED DAV1D TEST v2\n");
        out.append("Decoder source: APK-bundled libdav1dJNI.so\n");
        out.append("Android MediaCodec: NOT USED\n");

        Object decoder = null;
        Object output = null;
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

            // 2 input buffers, 2 output buffers, 64 KiB input buffer,
            // 2-frame delay, 2 dav1d threads, frame parallelism disabled.
            decoder = constructor.newInstance(2, 2, 65536, 2, 2, false);
            out.append("DECODER_INIT=PASS\n");
            out.append("DECODER_NAME=")
                    .append(String.valueOf(decoderClass.getMethod("getName").invoke(decoder)))
                    .append("\n");

            // Force raw YUV output for a standalone decoder smoke test.
            decoderClass.getMethod("setOutputMode", int.class).invoke(decoder, 0);

            byte[] frame = Base64.getDecoder().decode(TEST_FRAME_BASE64);
            Class<?> inputClass = Class.forName("androidx.media3.decoder.DecoderInputBuffer");
            Object input = decoderClass.getMethod("dequeueInputBuffer").invoke(decoder);
            if (input == null) {
                throw new IllegalStateException("No decoder input buffer was available");
            }

            inputClass.getMethod("ensureSpaceForWrite", int.class).invoke(input, frame.length);
            Field dataField = inputClass.getField("data");
            ByteBuffer data = (ByteBuffer) dataField.get(input);
            data.put(frame);
            inputClass.getField("timeUs").setLong(input, 0L);
            inputClass.getMethod("addFlag", int.class).invoke(input, 1);          // key frame
            inputClass.getMethod("addFlag", int.class).invoke(input, 134217728); // first sample
            inputClass.getMethod("flip").invoke(input);
            decoderClass.getMethod("queueInputBuffer", inputClass).invoke(decoder, input);
            out.append("AV1_TEST_BITSTREAM_BYTES=").append(frame.length).append("\n");

            Class<?> outputClass = Class.forName("androidx.media3.decoder.VideoDecoderOutputBuffer");
            Method dequeueOutput = decoderClass.getMethod("dequeueOutputBuffer");
            boolean decoded = false;
            long deadline = System.currentTimeMillis() + 5000L;
            while (System.currentTimeMillis() < deadline) {
                output = dequeueOutput.invoke(decoder);
                if (output != null) {
                    int width = outputClass.getField("width").getInt(output);
                    int height = outputClass.getField("height").getInt(output);
                    if (width > 0 && height > 0) {
                        out.append("DECODE_FRAME=PASS\n");
                        out.append("DECODED_SIZE=").append(width).append("x").append(height).append("\n");
                        decoded = true;
                    } else {
                        out.append("OUTPUT_BUFFER_WITHOUT_FRAME=").append(width).append("x").append(height).append("\n");
                    }
                    outputClass.getMethod("release").invoke(output);
                    output = null;
                    if (decoded) break;
                } else {
                    Thread.sleep(25L);
                }
            }

            if (!decoded) {
                out.append("DECODE_FRAME=FAIL_TIMEOUT_OR_NO_FRAME\n");
            }
        } catch (Throwable error) {
            Throwable cause = error;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            out.append("RESULT=FAIL\n");
            out.append("ERROR=").append(cause.getClass().getName())
                    .append(": ").append(String.valueOf(cause.getMessage())).append("\n");
        } finally {
            if (output != null) {
                try {
                    output.getClass().getMethod("release").invoke(output);
                } catch (Throwable ignored) {}
            }
            if (decoder != null) {
                try {
                    decoder.getClass().getMethod("release").invoke(decoder);
                    out.append("DECODER_RELEASE=PASS\n");
                } catch (Throwable releaseError) {
                    out.append("DECODER_RELEASE=FAIL: ")
                            .append(releaseError.getClass().getSimpleName()).append("\n");
                }
            }
        }
        return out.toString();
    }
}
