package com.season2.townlife.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class TownPathSavedDataTest {
    @Test
    void savingPathDataUsesVersionTwoForTypedPaths() {
        TownPathSavedData data = new TownPathSavedData();
        data.addAll(List.of(new BlockPos(4, 64, 9)));

        CompoundTag tag = new CompoundTag();
        data.save(tag);

        assertEquals(2, tag.getInt("DataVersion"));
    }
}
