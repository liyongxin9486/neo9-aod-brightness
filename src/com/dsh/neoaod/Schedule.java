package io.local.neoaod.typeguard;

import java.util.Calendar;
import java.util.TimeZone;

/** Daily local-time rules. Start inclusive, end exclusive; rule 2 wins overlaps. */
public final class Schedule {
    public static final class Rule {
        public final boolean enabled;
        public final int start, end, percent;
        public Rule(boolean enabled, int start, int end, int percent) {
            if (start < 0 || start >= 1440 || end < 0 || end >= 1440)
                throw new IllegalArgumentException("时间必须在 00:00–23:59 之间");
            checkPercent(percent);
            if (enabled && start == end) throw new IllegalArgumentException("启用时段的开始和结束时间不能相同");
            this.enabled = enabled; this.start = start; this.end = end; this.percent = percent;
        }
        public boolean contains(int minute) {
            return enabled && (start < end ? minute >= start && minute < end : minute >= start || minute < end);
        }
    }
    public final int manual;
    public final Rule first, second;
    public Schedule(int manual, Rule first, Rule second) {
        checkPercent(manual);
        if (first == null || second == null) throw new IllegalArgumentException("需要两组时段");
        this.manual = manual; this.first = first; this.second = second;
    }
    public static void checkPercent(int value) {
        if (value < 20 || value > 100) throw new IllegalArgumentException("亮度范围为 20%–100%");
    }
    public static Schedule defaults(int manual) {
        return new Schedule(Math.max(20, Math.min(100, manual)), new Rule(false, 480, 1320, 60), new Rule(false, 1320, 480, 30));
    }
    public int activeSlot(int minute) { return second.contains(minute) ? 2 : first.contains(minute) ? 1 : 0; }
    public int percentAt(int minute) { int slot = activeSlot(minute); return slot == 2 ? second.percent : slot == 1 ? first.percent : manual; }
    public static int minuteAt(long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone); c.setTimeInMillis(now);
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }
    public long nextBoundary(long now, TimeZone zone) {
        long next = Long.MAX_VALUE;
        for (Rule rule : new Rule[]{first, second}) {
            if (!rule.enabled) continue;
            for (int minute : new int[]{rule.start, rule.end}) {
                for (int day = 0; day <= 2; day++) {
                    Calendar c = Calendar.getInstance(zone); c.setTimeInMillis(now);
                    c.add(Calendar.DAY_OF_MONTH, day);
                    c.set(Calendar.HOUR_OF_DAY, minute / 60); c.set(Calendar.MINUTE, minute % 60);
                    c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
                    long candidate = c.getTimeInMillis();
                    if (candidate > now && candidate < next) next = candidate;
                }
            }
        }
        return next;
    }
}
