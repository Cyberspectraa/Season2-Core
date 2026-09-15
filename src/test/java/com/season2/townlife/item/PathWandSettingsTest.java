package com.season2.townlife.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.season2.townlife.data.TownPathType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

final class PathWandSettingsTest {
    @Test
    void settingsRoundTripOnTheWandStack() {
        ItemStack stack = new ItemStack(new Item(new Item.Properties()));

        PathWandSettings.set(stack, PathEditMode.REMOVE_CONNECTED, TownPathType.AVOID);

        assertEquals(PathEditMode.REMOVE_CONNECTED, PathWandSettings.mode(stack));
        assertEquals(TownPathType.AVOID, PathWandSettings.type(stack));
    }

    @Test
    void freshWandDefaultsToConnectedNormalPath() {
        ItemStack stack = new ItemStack(new Item(new Item.Properties()));

        assertEquals(PathEditMode.ADD_CONNECTED, PathWandSettings.mode(stack));
        assertEquals(TownPathType.NORMAL, PathWandSettings.type(stack));
    }
}
