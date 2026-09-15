package com.season2.townlife.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    @Test
    void legacyPathBlocksMigrateToNormalTypedEntries() {
        BlockPos first = new BlockPos(4, 64, 9);
        BlockPos second = new BlockPos(5, 64, 9);
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("DataVersion", 1);
        legacy.putLongArray("PathBlocks", new long[]{first.asLong(), second.asLong()});

        TownPathSavedData data = TownPathSavedData.load(legacy);
        CompoundTag migrated = new CompoundTag();
        data.save(migrated);

        ListTag entries = migrated.getList("PathEntries", Tag.TAG_COMPOUND);
        assertEquals(2, entries.size());
        assertEquals("NORMAL", entries.getCompound(0).getString("Type"));
        assertEquals("NORMAL", entries.getCompound(1).getString("Type"));
    }
}
