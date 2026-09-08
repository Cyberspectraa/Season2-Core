package com.season2.townlife.logic;

import java.util.Locale;

public enum LocationType {
    HOME,
    TOWN_CENTRE,
    FOOD_MARKET,
    TAVERN,
    WORKPLACE,
    GARDEN,
    SHELTER,
    ENTRANCE;

    public static LocationType parse(String value) {
        return LocationType.valueOf(value.toUpperCase(Locale.ROOT));
    }

    public int defaultOpenTime() {
        return switch (this) {
            case FOOD_MARKET -> 1000;
            case TAVERN -> 6000;
            case WORKPLACE -> 1500;
            default -> 0;
        };
    }

    public int defaultCloseTime() {
        return switch (this) {
            case TOWN_CENTRE -> 13000;
            case FOOD_MARKET -> 10000;
            case TAVERN -> 16000;
            case WORKPLACE -> 10000;
            case GARDEN -> 12500;
            default -> 0;
        };
    }
}
