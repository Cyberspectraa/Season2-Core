package com.season2.townlife.logic;

public enum NeedType {
    HUNGER,
    ENERGY,
    SOCIAL,
    FUN;

    public static NeedType parse(String value) {
        return NeedType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
