package com.season2.townlife.logic;

/** Dependency-free regressions; run with javac/java -ea on Java 17. */
public final class BreakScheduleTest {
    public static void main(String[] args) {
        var day = BreakSchedule.defaultFor(1800, 9800);
        assert day.start() == 5300 : "break should begin mid-shift";
        assert day.end() == 6300;
        assert !BreakSchedule.inBreak(5299, 1800, 9800, day.start(), day.end());
        assert BreakSchedule.inBreak(5300, 1800, 9800, day.start(), day.end());
        assert BreakSchedule.inBreak(6299, 1800, 9800, day.start(), day.end());
        assert !BreakSchedule.inBreak(6300, 1800, 9800, day.start(), day.end());
        assert !BreakSchedule.inBreak(9800, 1800, 9800, day.start(), day.end());
        var overnight = BreakSchedule.defaultFor(22000, 4000);
        assert BreakSchedule.valid(22000, 4000, overnight.start(), overnight.end());
        assert BreakSchedule.inBreak(overnight.start(), 22000, 4000, overnight.start(), overnight.end());
        assert !BreakSchedule.inBreak(4000, 22000, 4000, overnight.start(), overnight.end());
        assert !BreakSchedule.valid(1800, 9800, 9000, 10000);
        assert !BreakSchedule.valid(1800, 9800, 7000, 6500);
        assert BreakSchedule.defaultFor(1000, 1800).start() == -1 : "short shifts have no break";
        System.out.println("BreakScheduleTest PASS");
    }
}
