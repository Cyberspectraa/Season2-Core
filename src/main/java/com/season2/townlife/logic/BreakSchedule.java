package com.season2.townlife.logic;

/** Pure Minecraft-day tick arithmetic shared by resident scheduling and the Dev Clock. */
public final class BreakSchedule {
    private static final int DAY = 24000;
    private static final int DURATION = 1000;
    private static final int MIN_SHIFT = 2400;

    private BreakSchedule() {}

    public record Window(int start, int end) {}

    public static Window defaultFor(int workStart, int workEnd) {
        int duration = shiftLength(workStart, workEnd);
        if (duration < MIN_SHIFT) return new Window(-1, -1);
        int offset = (duration - DURATION) / 2;
        return new Window(Math.floorMod(workStart + offset, DAY),
                Math.floorMod(workStart + offset + DURATION, DAY));
    }

    public static boolean valid(int workStart, int workEnd, int breakStart, int breakEnd) {
        if (breakStart < 0 || breakStart >= DAY || breakEnd < 0 || breakEnd >= DAY) return false;
        int startOffset = Math.floorMod(breakStart - workStart, DAY);
        int endOffset = Math.floorMod(breakEnd - workStart, DAY);
        return startOffset > 0 && endOffset > startOffset && endOffset < shiftLength(workStart, workEnd);
    }

    public static boolean inBreak(long dayTime, int workStart, int workEnd, int breakStart, int breakEnd) {
        if (!valid(workStart, workEnd, breakStart, breakEnd)) return false;
        int offset = Math.floorMod((int) Math.floorMod(dayTime, DAY) - workStart, DAY);
        int startOffset = Math.floorMod(breakStart - workStart, DAY);
        int endOffset = Math.floorMod(breakEnd - workStart, DAY);
        return offset >= startOffset && offset < endOffset;
    }

    private static int shiftLength(int workStart, int workEnd) {
        int length = Math.floorMod(workEnd - workStart, DAY);
        return length == 0 ? DAY : length;
    }
}
