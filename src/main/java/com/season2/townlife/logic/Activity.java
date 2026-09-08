package com.season2.townlife.logic;

public enum Activity {
    IDLE,
    EAT,
    SLEEP,
    SOCIALISE,
    RELAX,
    WORK,
    SHELTER,
    RETURN_HOME;

    public boolean isBasicNeed() {
        return this == EAT || this == SLEEP;
    }
}
