import io.local.neoaod.typeguard.Schedule;
import java.util.Calendar;
import java.util.TimeZone;

public class ScheduleTest {
    private static int checks;
    private static void eq(long expected, long actual) {
        checks++; if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static long instant(String zone, int day, int hour, int minute) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone)); c.clear(); c.set(2026, 10, day, hour, minute); return c.getTimeInMillis();
    }
    public static void main(String[] args) {
        Schedule off = Schedule.defaults(60);
        eq(60, off.percentAt(0)); eq(60, off.percentAt(1439)); eq(Long.MAX_VALUE, off.nextBoundary(0, TimeZone.getTimeZone("UTC")));
        Schedule dayNight = new Schedule(65, new Schedule.Rule(true, 480, 1200, 80), new Schedule.Rule(true, 1320, 360, 30));
        // Full-day independent expected map: night [22:00,06:00), day [08:00,20:00).
        for (int m = 0; m < 1440; m++) eq(m < 360 || m >= 1320 ? 30 : m >= 480 && m < 1200 ? 80 : 65, dayNight.percentAt(m));
        Schedule overlap = new Schedule(60, new Schedule.Rule(true, 480, 1320, 75), new Schedule.Rule(true, 1200, 1380, 25));
        eq(75, overlap.percentAt(1199)); eq(25, overlap.percentAt(1200)); eq(25, overlap.percentAt(1320)); eq(60, overlap.percentAt(1380));
        TimeZone sh = TimeZone.getTimeZone("Asia/Shanghai");
        eq(instant("Asia/Shanghai", 2, 8, 0), dayNight.nextBoundary(instant("Asia/Shanghai", 2, 7, 59), sh));
        eq(instant("Asia/Shanghai", 2, 20, 0), dayNight.nextBoundary(instant("Asia/Shanghai", 2, 8, 0), sh));
        eq(instant("Asia/Shanghai", 3, 6, 0), dayNight.nextBoundary(instant("Asia/Shanghai", 2, 23, 59), sh));
        eq(480, Schedule.minuteAt(instant("Asia/Shanghai", 2, 8, 0), sh));
        eq(0, Schedule.minuteAt(instant("Asia/Shanghai", 2, 8, 0), TimeZone.getTimeZone("UTC")));
        boolean invalid = false;
        try { new Schedule.Rule(true, 480, 480, 30); } catch (IllegalArgumentException expected) { invalid = true; } eq(1, invalid ? 1 : 0);
        invalid = false;
        try { Schedule.defaults(60); new Schedule.Rule(true, 0, 60, 19); } catch (IllegalArgumentException expected) { invalid = true; } eq(1, invalid ? 1 : 0);
        new Schedule.Rule(false, 480, 480, 30);
        System.out.println("PASS: " + checks + " schedule checks");
    }
}
