package com.season2.townlife.logic;

/** Never overwrite an active Minecraft path just because a periodic tick elapsed. */
public final class PathRetryPolicy {
    private PathRetryPolicy() {}

    public static boolean shouldRequest(boolean firstAttempt, long now, long nextAllowed,
                                        boolean navigationDone, int stuckSeconds) {
        return firstAttempt || (now >= nextAllowed && (navigationDone || stuckSeconds >= 3));
    }
}
