package com.season2.townlife.logic;

public enum JobType {
    UNEMPLOYED,
    MARKET_VENDOR,
    SMITH_APPRENTICE,
    GARDENER,
    TAVERN_WORKER,
    WATCHPERSON;

    public static JobType parse(String value) {
        return JobType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    }
}
