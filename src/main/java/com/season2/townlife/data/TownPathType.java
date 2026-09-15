package com.season2.townlife.data;

import java.util.Locale;

/** Routing priority assigned to each registered Town Path surface block. */
public enum TownPathType {
    MAIN("Main Road", 0.65D),
    NORMAL("Normal Path", 1.00D),
    LOW("Low Priority", 2.25D),
    AVOID("Avoid", 8.00D);

    private final String displayName;
    private final double routeCost;

    TownPathType(String displayName, double routeCost) {
        this.displayName = displayName;
        this.routeCost = routeCost;
    }

    public String displayName() {
        return displayName;
    }

    public double routeCost() {
        return routeCost;
    }

    public static TownPathType fromSavedName(String value) {
        if (value == null || value.isBlank()) return NORMAL;
        try {
            return TownPathType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }
}
