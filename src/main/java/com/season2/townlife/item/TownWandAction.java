package com.season2.townlife.item;

/** Explicitly selected operation, never inferred from the sneak key. */
public enum TownWandAction {
    NONE("No action"),
    ASSIGN_HOME("Assign Home"),
    ASSIGN_WORKPLACE("Assign Workplace"),
    ASSIGN_POSITION("Assign Work Position"),
    CLEAR_POSITION("Clear Work Position"),
    CLEAR_SELECTION("Clear Selection");

    private final String label;
    TownWandAction(String label) { this.label = label; }
    public String label() { return label; }
    public boolean requiresBlock() {
        return this == ASSIGN_HOME || this == ASSIGN_WORKPLACE || this == ASSIGN_POSITION;
    }
    public static TownWandAction safeOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
    }
    public static TownWandAction safeName(String name) {
        if (name == null) return NONE;
        try { return valueOf(name); }
        catch (IllegalArgumentException exception) { return NONE; }
    }
}
