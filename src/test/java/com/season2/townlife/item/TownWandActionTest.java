package com.season2.townlife.item;

/** Pure-Java regression test for safe defaults and deliberate removal actions. */
public final class TownWandActionTest {
    public static void main(String[] args) {
        if (TownWandAction.safeOrdinal(-1) != TownWandAction.NONE) throw new AssertionError("negative mode");
        if (TownWandAction.safeOrdinal(100) != TownWandAction.NONE) throw new AssertionError("invalid mode");
        if (TownWandAction.safeName("UNRECOGNISED") != TownWandAction.NONE) throw new AssertionError("invalid persisted mode");
        if (TownWandAction.CLEAR_SELECTION.requiresBlock() || TownWandAction.CLEAR_POSITION.requiresBlock())
            throw new AssertionError("clear actions must be menu-only");
        for (TownWandAction action : TownWandAction.values()) {
            if (TownWandAction.safeName(action.name()) != action) throw new AssertionError(action);
        }
        System.out.println("TownWandActionTest passed");
    }
}
