package com.season2.townlife.logic;

import java.util.EnumMap;
import java.util.Map;

public final class Needs {
    private final EnumMap<NeedType, Float> values = new EnumMap<>(NeedType.class);

    public Needs() {
        for (NeedType need : NeedType.values()) {
            values.put(need, 75F);
        }
    }

    public float get(NeedType need) {
        return values.getOrDefault(need, 75F);
    }

    public void set(NeedType need, float value) {
        values.put(need, clamp(value));
    }

    public void add(NeedType need, float delta) {
        set(need, get(need) + delta);
    }

    public float lowest() {
        float minimum = 100F;
        for (float value : values.values()) {
            minimum = Math.min(minimum, value);
        }
        return minimum;
    }

    public float mood() {
        float total = 0F;
        for (NeedType type : NeedType.values()) {
            total += get(type);
        }
        float average = total / NeedType.values().length;
        return clamp(average * 0.72F + lowest() * 0.28F);
    }

    public Map<NeedType, Float> snapshot() {
        return Map.copyOf(values);
    }

    private static float clamp(float value) {
        return Math.max(0F, Math.min(100F, value));
    }
}
