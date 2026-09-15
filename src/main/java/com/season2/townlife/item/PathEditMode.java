package com.season2.townlife.item;

/** Editing operation currently selected on a Path Wand. */
public enum PathEditMode {
    ADD_CONNECTED("Add Connected"),
    ADD_SINGLE("Add Single"),
    REMOVE_SINGLE("Remove Single"),
    REMOVE_CONNECTED("Remove Connected"),
    INSPECT("Inspect");

    private final String displayName;

    PathEditMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static PathEditMode fromOrdinal(int ordinal) {
        PathEditMode[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : ADD_CONNECTED;
    }
}
