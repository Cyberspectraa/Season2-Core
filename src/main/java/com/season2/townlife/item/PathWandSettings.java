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
        return mode(stack == null ? null : stack.getTag());
    }

    public static TownPathType type(ItemStack stack) {
        return type(stack == null ? null : stack.getTag());
    }

    static PathEditMode mode(CompoundTag tag) {
        return tag == null ? PathEditMode.ADD_CONNECTED : PathEditMode.fromOrdinal(tag.getInt(MODE_KEY));
    }

    static TownPathType type(CompoundTag tag) {
        return tag == null ? TownPathType.NORMAL : TownPathType.fromSavedName(tag.getString(TYPE_KEY));
    }

    public static void set(ItemStack stack, PathEditMode mode, TownPathType type) {
        if (stack == null || stack.isEmpty()) return;
        set(stack.getOrCreateTag(), mode, type);
    }

    static void set(CompoundTag tag, PathEditMode mode, TownPathType type) {
        if (tag == null) return;
        PathEditMode safeMode = mode == null ? PathEditMode.ADD_CONNECTED : mode;
        TownPathType safeType = type == null ? TownPathType.NORMAL : type;
        tag.putInt(MODE_KEY, safeMode.ordinal());
        tag.putString(TYPE_KEY, safeType.name());
    }
}
