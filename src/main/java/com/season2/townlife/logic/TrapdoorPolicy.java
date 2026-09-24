package com.season2.townlife.logic;

/** Fail-closed eligibility; in-world proximity and shape checks live in runtime. */
public final class TrapdoorPolicy {
    private TrapdoorPolicy() {}

    public static boolean mayOpen(boolean traveling, boolean solidFloor, boolean powered,
                                  boolean loaded, boolean directlyAhead) {
        return traveling && solidFloor && !powered && loaded && directlyAhead;
    }
}
