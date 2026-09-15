package com.season2.townlife.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.season2.townlife.data.TownPathType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class PathWandSettingsTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void settingsRoundTripOnTheWandStack() {
        ItemStack stack = new ItemStack(Items.STICK);

        PathWandSettings.set(stack, PathEditMode.REMOVE_CONNECTED, TownPathType.AVOID);

        assertEquals(PathEditMode.REMOVE_CONNECTED, PathWandSettings.mode(stack));
        assertEquals(TownPathType.AVOID, PathWandSettings.type(stack));
    }

    @Test
    void freshWandDefaultsToConnectedNormalPath() {
        ItemStack stack = new ItemStack(Items.STICK);

        assertEquals(PathEditMode.ADD_CONNECTED, PathWandSettings.mode(stack));
        assertEquals(TownPathType.NORMAL, PathWandSettings.type(stack));
    }
}
