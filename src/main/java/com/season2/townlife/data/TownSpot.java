package com.season2.townlife.data;

import com.season2.townlife.logic.SpotKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

public record TownSpot(SpotKind kind, BlockPos pos, Direction facing) {
    public TownSpot {
        pos = pos.immutable();
        facing = facing == null ? Direction.NORTH : facing;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Kind", kind.name());
        tag.putLong("Pos", pos.asLong());
        tag.putString("Facing", facing.getName());
        return tag;
    }

    public static TownSpot load(CompoundTag tag) {
        SpotKind kind;
        try {
            kind = SpotKind.valueOf(tag.getString("Kind"));
        } catch (IllegalArgumentException ex) {
            kind = SpotKind.ROAM;
        }
        Direction facing = Direction.byName(tag.getString("Facing"));
        return new TownSpot(kind, BlockPos.of(tag.getLong("Pos")), facing == null ? Direction.NORTH : facing);
    }
}
