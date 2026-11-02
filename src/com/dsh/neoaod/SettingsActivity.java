package io.local.neoaod.typeguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.util.TimeZone;

public final class SettingsActivity extends Activity {
    private static final int INK = Color.rgb(27, 43, 36), MUTED = Color.rgb(100, 117, 108), GREEN = Color.rgb(35, 109, 77);
    private int manual;
    private final DraftRule[] rules = new DraftRule[2];
    private TextView status, saveNote;
    private boolean dirty;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusTick = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 60000 - System.currentTimeMillis() % 60000); }
    };
    private static final class DraftRule {
        boolean enabled; int start, end, percent;
        DraftRule(Schedule.Rule rule) { enabled = rule.enabled; start = rule.start; end = rule.end; percent = rule.percent; }
        Schedule.Rule toRule() { return new Schedule.Rule(enabled, start, end, percent); }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Schedule initial = ConfigStore.read(getContentResolver());
        manual = initial.manual; rules[0] = new DraftRule(initial.first); rules[1] = new DraftRule(initial.second);
        if (state != null && state.containsKey("manual")) {
            manual = state.getInt("manual"); dirty = state.getBoolean("dirty");
            for (int i = 0; i < 2; i++) { rules[i].enabled = state.getBoolean("e" + i); rules[i].start = state.getInt("s" + i); rules[i].end = state.getInt("t" + i); rules[i].percent = state.getInt("p" + i); }
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.rgb(242, 246, 243));
        LinearLayout body = column(); body.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            body.setPadding(dp(20), insets.getSystemWindowInsetTop() + dp(16), dp(20), insets.getSystemWindowInsetBottom() + dp(24));
            return insets;
        });
        TextView eyebrow = text("NEO9  /  AOD", 12, GREEN); eyebrow.setLetterSpacing(.12f); body.addView(eyebrow);
        TextView title = text("息屏亮度", 30, INK); title.setTypeface(null, Typeface.BOLD); add(body, title, 8);
        status = text("", 13, MUTED); add(body, status, 8);

        LinearLayout manualCard = card(); add(body, manualCard, 20);
        LinearLayout manualHeader = row(); manualCard.addView(manualHeader);
        TextView manualLabel = text("默认亮度", 17, INK); manualLabel.setTypeface(null, Typeface.BOLD);
        manualHeader.addView(manualLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView manualValue = text(manual + "%", 21, GREEN); manualHeader.addView(manualValue);
        SeekBar manualSlider = slider(manual, "默认亮度"); manualCard.addView(manualSlider, new LinearLayout.LayoutParams(-1, dp(42)));
        manualSlider.setOnSeekBarChangeListener(new SliderListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                manual = value + 20; manualValue.setText(manual + "%"); if (fromUser) changed();
            }
        });
        manualCard.addView(text("未命中时段时使用 · 数字越小越暗", 12, MUTED));
        for (int i = 0; i < 2; i++) add(body, ruleCard(i), 12);
        TextView hint = text("每天重复，支持跨午夜。开始时间包含在内，结束时间不包含；时段重叠时，时段 2 优先。", 12, MUTED);
        hint.setLineSpacing(dp(3), 1f); add(body, hint, 14);
        Button save = button("保存设置", GREEN, Color.WHITE); save.setContentDescription("保存设置"); add(body, save, 16);
        save.setOnClickListener(v -> save());
        saveNote = text(dirty ? "有未保存的修改" : "设置已保存", 12, MUTED); saveNote.setGravity(Gravity.CENTER); add(body, saveNote, 8);
        TextView foot = text("仅调整息屏显示，不改变正常亮屏亮度。\n100% 为原始显示强度，最低可调至 20%。", 12, MUTED);
        foot.setLineSpacing(dp(3), 1f); add(body, foot, 16);
        setContentView(scroll);
    }
    private LinearLayout ruleCard(int index) {
        DraftRule rule = rules[index]; String name = "时段 " + (index + 1);
        LinearLayout card = card(); LinearLayout header = row(); card.addView(header);
        TextView label = text(name, 17, INK); label.setTypeface(null, Typeface.BOLD); header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this); toggle.setContentDescription(name + "开关"); toggle.setChecked(rule.enabled); header.addView(toggle);
        LinearLayout times = row(); add(card, times, 10);
        Button start = button(time(rule.start), Color.rgb(237, 244, 239), INK);
        Button end = button(time(rule.end), Color.rgb(237, 244, 239), INK);
        start.setContentDescription(name + "开始时间 " + time(rule.start)); end.setContentDescription(name + "结束时间 " + time(rule.end));
        times.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1));
        TextView to = text("至", 13, MUTED); to.setGravity(Gravity.CENTER); times.addView(to, new LinearLayout.LayoutParams(dp(32), -2));
        times.addView(end, new LinearLayout.LayoutParams(0, dp(44), 1));
        start.setOnClickListener(v -> pickTime(index, true, start)); end.setOnClickListener(v -> pickTime(index, false, end));
        LinearLayout brightness = row(); add(card, brightness, 8);
        TextView amount = text(rule.percent + "%", 17, GREEN); amount.setGravity(Gravity.END);
        SeekBar slider = slider(rule.percent, name + "亮度"); brightness.addView(slider, new LinearLayout.LayoutParams(0, dp(40), 1)); brightness.addView(amount, new LinearLayout.LayoutParams(dp(54), -2));
        slider.setOnSeekBarChangeListener(new SliderListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                rule.percent = value + 20; amount.setText(rule.percent + "%"); if (fromUser) changed();
            }
        });
        Runnable enabled = () -> { start.setEnabled(rule.enabled); end.setEnabled(rule.enabled); slider.setEnabled(rule.enabled); times.setAlpha(rule.enabled ? 1f : .45f); brightness.setAlpha(rule.enabled ? 1f : .45f); };
        enabled.run(); toggle.setOnCheckedChangeListener((v, value) -> { rule.enabled = value; enabled.run(); changed(); });
        return card;
    }
    private void pickTime(int index, boolean isStart, Button button) {
        DraftRule rule = rules[index]; int minute = isStart ? rule.start : rule.end;
        TimePickerDialog dialog = new TimePickerDialog(this, (picker, h, m) -> {
            int value = h * 60 + m; if (isStart) rule.start = value; else rule.end = value;
            button.setText(time(value)); button.setContentDescription("时段 " + (index + 1) + (isStart ? "开始时间 " : "结束时间 ") + time(value)); changed();
        }, minute / 60, minute % 60, true);
        dialog.setTitle("时段 " + (index + 1) + (isStart ? " · 开始时间" : " · 结束时间")); dialog.show();
    }
    private void save() {
        try {
            Schedule value = draft();
            if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(this).setTitle("暂时无法保存").setMessage("设置写入权限尚未就绪，请完成首次安装授权后再试。已有息屏配置不受影响。")
                        .setPositiveButton("知道了", null).show(); return;
            }
            if (!ConfigStore.save(getContentResolver(), value)) throw new IllegalStateException("系统未接受设置，请重试");
            dirty = false; saveNote.setText("已保存 · 息屏时自动应用"); updateStatus();
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { new AlertDialog.Builder(this).setTitle("请检查设置").setMessage(e.getMessage()).setPositiveButton("知道了", null).show(); }
    }
    private Schedule draft() { return new Schedule(manual, rules[0].toRule(), rules[1].toRule()); }
    private void changed() { dirty = true; if (saveNote != null) saveNote.setText("有未保存的修改"); }
    private void updateStatus() {
        if (status == null) return;
        Schedule s = ConfigStore.read(getContentResolver()); int minute = Schedule.minuteAt(System.currentTimeMillis(), TimeZone.getDefault()); int slot = s.activeSlot(minute);
        status.setText("当前规则：" + (slot == 0 ? "默认亮度" : "时段 " + slot) + "  ·  " + s.percentAt(minute) + "%");
    }
    @Override protected void onResume() { super.onResume(); statusTick.run(); }
    @Override protected void onPause() { handler.removeCallbacks(statusTick); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        // Invalid drafts (equal start/end) are still editable; preserve every field.
        out.putInt("manual", manual);
        for (int i = 0; i < 2; i++) { out.putBoolean("e" + i, rules[i].enabled); out.putInt("s" + i, rules[i].start); out.putInt("t" + i, rules[i].end); out.putInt("p" + i, rules[i].percent); }
        out.putBoolean("dirty", dirty); super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() {
        if (!dirty) { super.onBackPressed(); return; }
        new AlertDialog.Builder(this).setTitle("修改尚未保存").setMessage("离开前要保存设置吗？")
                .setPositiveButton("保存", (d, w) -> { save(); if (!dirty) finish(); })
                .setNegativeButton("放弃修改", (d, w) -> finish()).setNeutralButton("继续编辑", null).show();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private GradientDrawable background(int color, int radius) { GradientDrawable b = new GradientDrawable(); b.setColor(color); b.setCornerRadius(dp(radius)); return b; }
    private LinearLayout card() { LinearLayout l = column(); l.setPadding(dp(16), dp(14), dp(16), dp(14)); l.setBackground(background(Color.WHITE, 20)); return l; }
    private void add(LinearLayout parent, View view, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(view, p); }
    private Button button(String value, int bg, int fg) { Button b = new Button(this); b.setText(value); b.setTextSize(15); b.setAllCaps(false); b.setTextColor(fg); b.setMinHeight(dp(46)); b.setMinimumHeight(dp(46)); b.setBackgroundTintList(null); b.setBackground(background(bg, 12)); return b; }
    private SeekBar slider(int value, String description) { SeekBar s = new SeekBar(this); s.setMax(80); s.setProgress(value - 20); s.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN)); s.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN)); s.setContentDescription(description); return s; }
    private static String time(int minute) { return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60); }
    private abstract static class SliderListener implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
