package com.season2.townlife.client;

import com.season2.townlife.data.TownPathType;
import com.season2.townlife.item.PathEditMode;
import net.minecraft.world.InteractionHand;

/** Common-side bridge that avoids direct client-class references from network handlers. */
public final class TownPathClientBridge {
    @FunctionalInterface
    public interface ConfigOpener {
        void open(PathEditMode mode, TownPathType type, InteractionHand hand);
    }

    private static ConfigOpener opener = (mode, type, hand) -> {};

    private TownPathClientBridge() {}

    public static void setConfigOpener(ConfigOpener next) {
        opener = next == null ? (mode, type, hand) -> {} : next;
    }

    public static void openConfig(PathEditMode mode, TownPathType type, InteractionHand hand) {
        opener.open(mode, type, hand);
    }
}
