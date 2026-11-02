package io.local.neoaod.typeguard;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.WeakHashMap;

/** Lives inside each hooked host, so closing the settings app does not stop rules. */
public final class BrightnessRuntime {
    private static BrightnessRuntime instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final WeakHashMap<View, Target> targets = new WeakHashMap<>();
    private final AlarmManager alarms;
    private final PendingIntent boundary;
    private int percent = 60;
    private long scheduledAt = Long.MAX_VALUE;
    private int lastSlot = -1, lastPercent = -1;

    public static BrightnessRuntime get(Context context) {
        if (instance == null) instance = new BrightnessRuntime(context);
        return instance;
    }
    private BrightnessRuntime(Context original) {
        Context app = original.getApplicationContext();
        context = app == null ? original : app;
        alarms = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        String action = context.getPackageName() + ".NEO9_AOD_BRIGHTNESS_BOUNDARY";
        boundary = PendingIntent.getBroadcast(context, 9041, new Intent(action).setPackage(context.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (action.equals(i.getAction())) scheduledAt = Long.MAX_VALUE;
                refresh();
            }
        };
        IntentFilter filter = new IntentFilter(action);
        filter.addAction(Intent.ACTION_TIME_CHANGED); filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(receiver, filter);
        ContentObserver observer = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { refresh(); }
        };
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(ConfigStore.KEY), false, observer);
        context.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(ConfigStore.LEGACY_KEY), false, observer);
        refresh();
    }
    public void setActive(View root, boolean active) {
        Target target = targets.get(root);
        if (target == null) {
            target = new Target(root, active);
            targets.put(root, target);
            root.getOverlay().add(target.shade);
            root.addOnLayoutChangeListener(target);
            root.addOnAttachStateChangeListener(target);
            refresh();
        } else if (target.active != active) {
            target.active = active;
            refresh();
        } else apply(target);
    }
    private void apply(Target target) {
        View root = target.root.get();
        if (root == null) return;
        int color = target.active ? Color.argb(Math.round(255 * (1f - percent / 100f)), 0, 0, 0) : 0;
        boolean resized = target.shade.getBounds().width() != root.getWidth() || target.shade.getBounds().height() != root.getHeight();
        if (target.shade.getColor() != color || resized) {
            target.shade.setColor(color);
            target.shade.setBounds(0, 0, root.getWidth(), root.getHeight());
            root.invalidate();
        }
    }
    private void refresh() {
        try {
            Schedule schedule = ConfigStore.read(context.getContentResolver());
            long now = System.currentTimeMillis(); TimeZone zone = TimeZone.getDefault();
            int minute = Schedule.minuteAt(now, zone);
            percent = schedule.percentAt(minute);
            int slot = schedule.activeSlot(minute);
            boolean active = false;
            for (Target target : new ArrayList<>(targets.values())) { apply(target); active |= target.active; }
            long next = active ? schedule.nextBoundary(now, zone) : Long.MAX_VALUE;
            if (scheduledAt != next) {
                alarms.cancel(boundary);
                scheduledAt = Long.MAX_VALUE;
                if (next != Long.MAX_VALUE) {
                    alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, boundary);
                    scheduledAt = next;
                }
                Log.i("NeoAODBrightness", "host=" + context.getPackageName() + " next=" + scheduledAt);
            }
            if (lastSlot != slot || lastPercent != percent) {
                Log.i("NeoAODBrightness", "host=" + context.getPackageName() + " slot=" + slot + " percent=" + percent);
                lastSlot = slot; lastPercent = percent;
            }
        } catch (Throwable t) { Log.e("NeoAODBrightness", "Refresh failed", t); }
    }
    private final class Target implements View.OnLayoutChangeListener, View.OnAttachStateChangeListener {
        final WeakReference<View> root;
        final ColorDrawable shade = new ColorDrawable(0);
        boolean active;
        Target(View view, boolean active) { root = new WeakReference<>(view); this.active = active; }
        @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) { apply(this); }
        @Override public void onViewAttachedToWindow(View v) { apply(this); }
        @Override public void onViewDetachedFromWindow(View v) {
            v.getOverlay().remove(shade); v.removeOnLayoutChangeListener(this); v.removeOnAttachStateChangeListener(this);
            targets.remove(v); refresh();
        }
    }
}
