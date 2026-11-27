package app.ibrahim.youtube;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.lang.reflect.Constructor;

/** Native in-player entry point. The panel contains buffer controls only. */
public final class IbrahimBufferControlButton {
    private static final String BUTTON_TAG = "ibrahim-buffer-control-button";

    private IbrahimBufferControlButton() {}

    public static void initializeButton(View sourceButton) {
        if (sourceButton == null || !(sourceButton.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup parent = (ViewGroup) sourceButton.getParent();
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (BUTTON_TAG.equals(parent.getChildAt(i).getTag())) {
                return;
            }
        }

        Context context = sourceButton.getContext();
        IbrahimBufferPolicy.setApplicationContext(context);

        TextView button = new TextView(context);
        button.setTag(BUTTON_TAG);
        button.setText("إبراهيم");
        button.setContentDescription("إعدادات إبراهيم للتحكم في البفر");
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setGravity(android.view.Gravity.CENTER);
        button.setSingleLine(true);
        button.setFocusable(true);
        button.setClickable(true);
        button.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        button.setTextColor(sourceButton instanceof TextView
                ? ((TextView) sourceButton).getCurrentTextColor()
                : Color.WHITE);

        Drawable background = sourceButton.getBackground();
        if (background != null && background.getConstantState() != null) {
            button.setBackground(background.getConstantState().newDrawable().mutate());
        }

        button.setOnClickListener(view -> showPanel(view.getContext()));

        ViewGroup.LayoutParams sourceParams = sourceButton.getLayoutParams();
        ViewGroup.LayoutParams newParams = cloneLayoutParams(sourceParams);
        int sourceIndex = parent.indexOfChild(sourceButton);
        parent.addView(button, Math.min(parent.getChildCount(), sourceIndex + 1), newParams);
    }

    private static ViewGroup.LayoutParams cloneLayoutParams(ViewGroup.LayoutParams source) {
        if (source == null) {
            return new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        try {
            Constructor<?> constructor = source.getClass().getConstructor(ViewGroup.LayoutParams.class);
            return (ViewGroup.LayoutParams) constructor.newInstance(source);
        } catch (Throwable ignored) {
            if (source instanceof ViewGroup.MarginLayoutParams) {
                return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) source);
            }
            return new ViewGroup.LayoutParams(source.width, source.height);
        }
    }

    private static void showPanel(Context context) {
        IbrahimBufferPolicy.setApplicationContext(context);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int side = dp(context, 20);
        content.setPadding(side, dp(context, 8), side, dp(context, 4));

        CheckBox enabled = new CheckBox(context);
        enabled.setText("تفعيل التحكم الذكي في البفر الأمامي");
        enabled.setChecked(IbrahimBufferPolicy.isEnabled(context));
        content.addView(enabled);

        TextView targetLabel = new TextView(context);
        SeekBar targetBar = new SeekBar(context);
        targetBar.setMax(9);
        targetBar.setProgress(IbrahimBufferPolicy.getTargetSeconds(context) - 1);
        targetLabel.setText("Target: " + (targetBar.getProgress() + 1) + " ثانية");
        content.addView(targetLabel);
        content.addView(targetBar);

        TextView resumeLabel = new TextView(context);
        SeekBar resumeBar = new SeekBar(context);
        resumeBar.setMax(Math.max(0, targetBar.getProgress() - 1));
        resumeBar.setProgress(Math.min(
                resumeBar.getMax(),
                IbrahimBufferPolicy.getResumeSeconds(context)
        ));
        resumeLabel.setText("Resume: " + resumeBar.getProgress() + " ثانية");
        content.addView(resumeLabel);
        content.addView(resumeBar);

        targetBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int target = progress + 1;
                targetLabel.setText("Target: " + target + " ثانية");
                resumeBar.setMax(Math.max(0, target - 1));
                if (resumeBar.getProgress() > resumeBar.getMax()) {
                    resumeBar.setProgress(resumeBar.getMax());
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        resumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                resumeLabel.setText("Resume: " + progress + " ثانية");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView explanation = new TextView(context);
        explanation.setText("عند بلوغ Target يتوقف طلب مقاطع جديدة، ويستأنف عند هبوط البفر إلى Resume. قيمة Resume أقل من Target لتجنب التبديل المتكرر.");
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        content.addView(explanation);

        new AlertDialog.Builder(context)
                .setTitle("إبراهيم · البفر فقط")
                .setView(content)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", (dialog, which) -> {
                    int target = targetBar.getProgress() + 1;
                    int resume = Math.min(resumeBar.getProgress(), target - 1);
                    IbrahimBufferPolicy.preferences(context).edit()
                            .putBoolean(IbrahimBufferPolicy.ENABLED, enabled.isChecked())
                            .putInt(IbrahimBufferPolicy.TARGET_SECONDS, target)
                            .putInt(IbrahimBufferPolicy.RESUME_SECONDS, resume)
                            .apply();
                    IbrahimBufferPolicy.resetState();
                })
                .show();
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
