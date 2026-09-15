package com.season2.townlife.item;

import com.season2.townlife.data.TownPathType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Small item-NBT settings store for the operator Path Wand. */
public final class PathWandSettings {
    private static final String MODE_KEY = "TownLifePathMode";
    private static final String TYPE_KEY = "TownLifePathType";

    private PathWandSettings() {}

    public static PathEditMode mode(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? PathEditMode.ADD_CONNECTED : PathEditMode.fromOrdinal(tag.getInt(MODE_KEY));
    }

    public static TownPathType type(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? TownPathType.NORMAL : TownPathType.fromSavedName(tag.getString(TYPE_KEY));
    }

    public static void set(ItemStack stack, PathEditMode mode, TownPathType type) {
        if (stack == null || stack.isEmpty()) return;
        PathEditMode safeMode = mode == null ? PathEditMode.ADD_CONNECTED : mode;
        TownPathType safeType = type == null ? TownPathType.NORMAL : type;
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(MODE_KEY, safeMode.ordinal());
        tag.putString(TYPE_KEY, safeType.name());
    }
}
