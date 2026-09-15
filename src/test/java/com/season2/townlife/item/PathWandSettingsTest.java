package com.season2.townlife.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.season2.townlife.data.TownPathType;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class PathWandSettingsTest {
    @Test
    void settingsRoundTripInWandNbt() {
        CompoundTag tag = new CompoundTag();

        PathWandSettings.set(tag, PathEditMode.REMOVE_CONNECTED, TownPathType.AVOID);

        assertEquals(PathEditMode.REMOVE_CONNECTED, PathWandSettings.mode(tag));
        assertEquals(TownPathType.AVOID, PathWandSettings.type(tag));
    }

    @Test
    void freshWandDefaultsToConnectedNormalPath() {
        assertEquals(PathEditMode.ADD_CONNECTED, PathWandSettings.mode((CompoundTag) null));
        assertEquals(TownPathType.NORMAL, PathWandSettings.type((CompoundTag) null));
    }
}
