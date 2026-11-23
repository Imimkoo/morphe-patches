/*
 * Copyright 2026.
 * Ibrahim Universal Video Control - independent implementation.
 */
package app.morphe.extension.youtube.videoplayer;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageInfo;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.lang.reflect.Method;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.spoof.requests.StreamingDataRequest;
import app.morphe.extension.shared.spoof.ClientType;
import app.morphe.extension.youtube.patches.VideoInformation;

/**
 * Independent codec and load-control policy. Does not delegate to the upstream
 * DisableVideoCodecsPatch or PlaybackBufferPatch implementations.
 */
@SuppressWarnings("unused")
public final class IbrahimVideoControlButton {
    private static final String PREFS = "ibrahim_universal_video_control";
    private static final String MODE_KEY = "codec_mode_v2";
    private static final String BUFFER_ENABLED_KEY = "buffer_enabled";
    private static final String BUFFER_TARGET_KEY = "buffer_target_seconds";
    private static final String BUFFER_RESUME_KEY = "buffer_resume_seconds";
    private static final String AUDIO_ONLY_KEY = "audio_only";

    public static final int DEFAULT = 0;
    public static final int PREFER_AV1 = 1;
    public static final int FORCE_AV1_ONLY = 2;

    private static volatile Context appContext;
    private static volatile boolean bufferThrottled;
    private static volatile String lastRendererState = "UNKNOWN";

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

    private static Context getContext() {
        Context context = appContext;
        return context != null ? context : Utils.getContext();
    }

    private static boolean isAudioOnly(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(AUDIO_ONLY_KEY, false);
    }

    public static ClientType getPreferredAv1Client(ClientType requested) {
        Context context = getContext();
        if (context == null) return ClientType.VISIONOS_1_06_AV1;
        if (isAudioOnly(context) || getMode(context) == DEFAULT) return requested;
        // Prefer the AV1-capable visionOS device profile that does not require the
        // JavaScript/PoToken path used by TV_SIMPLY. TV_SIMPLY and TV_SABR can be
        // skipped entirely when the embedded JS engine is unavailable.
        if (requested == ClientType.ANDROID_VR_SABR
                || requested == ClientType.ANDROID_VR_DASH
                || requested == ClientType.ANDROID_VR_EXPERIMENTAL) {
            return ClientType.ANDROID_XR_EXPERIMENTAL;
        }
        // The 1.06 AV1 profile deliberately uses the 17,1 device model that YouTube
        // documents as receiving AV1 streams, while retaining the no-JS visionOS path.
        return ClientType.VISIONOS_1_06_AV1;
    }

    private static boolean isBufferEnabled(Context context) {
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
                    "Prefer AV1 — اختيار AV1 أولًا مع السماح بالبدائل المتوافقة",
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

            CheckBox audioOnly = new CheckBox(context);
            audioOnly.setText("تشغيل الفيديوهات كصوت فقط");
            audioOnly.setChecked(isAudioOnly(context));
            content.addView(audioOnly);

            CheckBox bufferEnabled = new CheckBox(context);
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
                    + "\n" + av1DecoderSummary()
                    + "\nالتغييرات تطبق أثناء قرارات التحميل؛ سياسة الترميز قد تتطلب إعادة تحميل الفيديو.");
            content.addView(liveInfo);

            Button decoderProbeButton = new Button(context);
            decoderProbeButton.setText("اختبار مفكك AV1 المدمج داخل التطبيق");
            decoderProbeButton.setOnClickListener(view -> IbrahimAv1DecoderProbe.run(context));
            content.addView(decoderProbeButton);

            Button diagnosticButton = new Button(context);
            diagnosticButton.setText("جمع تقرير تشخيص الفيديو");
            diagnosticButton.setOnClickListener(view -> runDiagnostic(context));
            content.addView(diagnosticButton);

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
                                .putBoolean(AUDIO_ONLY_KEY, audioOnly.isChecked())
                                .putBoolean(BUFFER_ENABLED_KEY, bufferEnabled.isChecked())
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


    /**
     * Collects diagnostics only after an explicit user tap. No background polling or
     * periodic codec scans are performed.
     */
    private static void runDiagnostic(Context context) {
        final AlertDialog progress = new AlertDialog.Builder(context)
                .setTitle("تشخيص الفيديو")
                .setMessage("جاري فحص الجهاز ومعلومات التشغيل...")
                .setCancelable(false)
                .create();
        progress.show();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            String report = buildDiagnosticReport(context);
            progress.dismiss();

            TextView reportView = new TextView(context);
            int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
            reportView.setPadding(pad, pad, pad, pad);
            reportView.setTextIsSelectable(true);
            reportView.setText(report);

            ScrollView scroll = new ScrollView(context);
            scroll.addView(reportView);
            new AlertDialog.Builder(context)
                    .setTitle("تقرير تشخيص الفيديو")
                    .setView(scroll)
                    .setPositiveButton("نسخ التقرير", (dialog, which) -> {
                        ClipboardManager clipboard = (ClipboardManager)
                                context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Ibrahim video diagnostics", report));
                            Utils.showToastShort("تم نسخ التقرير");
                        }
                    })
                    .setNegativeButton("إغلاق", null)
                    .show();
        }, 1800L);
    }

    private static String buildDiagnosticReport(Context context) {
        StringBuilder out = new StringBuilder(4096);
        out.append("IBRAHIM VIDEO DIAGNOSTIC v1\\n");
        out.append("Collected: ").append(new java.util.Date()).append("\\n");
        out.append("\\n[APP]\\n");
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            out.append("package=").append(context.getPackageName()).append("\\n");
            out.append("versionName=").append(info.versionName).append("\\n");
            if (Build.VERSION.SDK_INT >= 28) out.append("versionCode=").append(info.getLongVersionCode()).append("\\n");
            else out.append("versionCode=").append(info.versionCode).append("\\n");
        } catch (Exception ex) {
            out.append("packageInfoError=").append(ex.getClass().getSimpleName()).append("\\n");
        }

        out.append("\\n[DEVICE]\\n")
                .append("manufacturer=").append(Build.MANUFACTURER).append("\\n")
                .append("model=").append(Build.MODEL).append("\\n")
                .append("device=").append(Build.DEVICE).append("\\n")
                .append("product=").append(Build.PRODUCT).append("\\n")
                .append("hardware=").append(Build.HARDWARE).append("\\n")
                .append("board=").append(Build.BOARD).append("\\n")
                .append("socModel=").append(Build.VERSION.SDK_INT >= 31 ? Build.SOC_MODEL : "API<31").append("\\n")
                .append("android=").append(Build.VERSION.RELEASE).append("\\n")
                .append("sdk=").append(Build.VERSION.SDK_INT).append("\\n")
                .append("supportedAbis=").append(java.util.Arrays.toString(Build.SUPPORTED_ABIS)).append("\\n");

        out.append("\\n[CURRENT VIDEO]\\n")
                .append(currentVideoSummary()).append("\\n")
                .append("spoofClient=").append(StreamingDataRequest.getLastSpoofedClientName()).append("\\n")
                .append("streamResponseFormats=\\n")
                .append(StreamingDataRequest.getLastStreamFormatsSummary()).append("\\n");

        out.append("\\n[EMBEDDED APP AV1 DECODER]\\n")
                .append(av1DecoderSummary()).append("\\n")
                .append("AV1 playback uses the APK-bundled dav1d JNI library, not Android MediaCodec.\\n");
        out.append("\\n[SYSTEM MEDIA CODECS - INFO ONLY; NOT USED BY EMBEDDED AV1]\\n");
        try {
            MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            int found = 0;
            for (MediaCodecInfo codec : codecs) {
                if (codec.isEncoder()) continue;
                for (String type : codec.getSupportedTypes()) {
                    String mime = type.toLowerCase(Locale.ROOT);
                    if (!mime.startsWith("video/")) continue;
                    if (!(mime.equals("video/av01") || mime.equals("video/x-vnd.on2.vp9")
                            || mime.equals("video/avc") || mime.equals("video/hevc")
                            || mime.equals("video/x-vnd.on2.vp8"))) continue;
                    found++;
                    out.append(codec.getName()).append(" | ").append(mime);
                    if (Build.VERSION.SDK_INT >= 29) {
                        out.append(" | ").append(codec.isHardwareAccelerated() ? "HW" : "SW")
                                .append(" | vendor=").append(codec.isVendor());
                    }
                    try {
                        MediaCodecInfo.CodecCapabilities caps = codec.getCapabilitiesForType(type);
                        MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                        if (video != null) {
                            out.append(" | 480p30=").append(video.areSizeAndRateSupported(854, 480, 30))
                                    .append(" | 720p30=").append(video.areSizeAndRateSupported(1280, 720, 30))
                                    .append(" | 720p60=").append(video.areSizeAndRateSupported(1280, 720, 60))
                                    .append(" | 1080p30=").append(video.areSizeAndRateSupported(1920, 1080, 30));
                        }
                    } catch (Exception capError) {
                        out.append(" | capabilityError=").append(capError.getClass().getSimpleName());
                    }
                    out.append("\\n");
                }
            }
            out.append("videoDecoderEntries=").append(found).append("\\n");
        } catch (Exception ex) {
            out.append("codecScanError=").append(ex.getClass().getSimpleName()).append(": ")
                    .append(ex.getMessage()).append("\\n");
        }

        out.append("\\n[IMPORTANT]\\n")
                .append("Codec inventory means Android advertises a decoder; it does NOT prove a frame was decoded.\\n")
                .append("streamResponseFormats are formats offered by the last spoof response, not necessarily the selected track.\\n")
                .append("No continuous monitoring or background collection is active.\\n");
        return out.toString();
    }

    /** Reports the AV1 decoders Android advertises; this is inventory, not a decode test. */
    /** Reports the app-bundled dav1d library, not codecs provided by the phone. */
    private static String av1DecoderSummary() {
        try {
            Class<?> library = Class.forName("androidx.media3.decoder.av1.Dav1dLibrary");
            Method available = library.getMethod("a");
            boolean loaded = Boolean.TRUE.equals(available.invoke(null));
            Class.forName("androidx.media3.decoder.av1.Dav1dDecoder");
            Class.forName("androidx.media3.decoder.av1.Libdav1dVideoRenderer");
            return "مفكك dav1d المدمج داخل التطبيق: "
                    + (loaded ? "متاح ومحمّل" : "غير متاح")
                    + " | Decoder وRenderer موجودان داخل APK"
                    + " | renderer=" + lastRendererState;
        } catch (Throwable ex) {
            return "فحص مفكك dav1d المدمج فشل: " + ex.getClass().getSimpleName()
                    + ": " + String.valueOf(ex.getMessage());
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

    /** Called by YouTube's renderer splice when its active renderer changes. */
    public static void setRendererState(String state) {
        if (state != null) lastRendererState = state;
    }

    /** Hook: controls whether YouTube may use VP9. */
    public static boolean allowVP9() {
        Context context = getContext();
        return context != null && getMode(context) == DEFAULT;
    }

    /**
     * Hook: filters video MIME types in Force AV1 only mode.
     * Audio codecs are deliberately left untouched.
     */
    public static boolean allowCodec(String mimeType) {
        Context context = getContext();
        if (context == null) return true;
        if (isAudioOnly(context)) {
            return mimeType == null || !mimeType.toLowerCase(java.util.Locale.ROOT).startsWith("video/");
        }
        if (getMode(context) != FORCE_AV1_ONLY) return true;
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
        Context context = getContext();
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
