package com.season2.townlife.client;

import com.season2.townlife.network.TownWandNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Isolate client screen code from the server-side network handler. */
@OnlyIn(Dist.CLIENT)
public final class TownWandClientBridge {
    private TownWandClientBridge() {}

    public static void open(TownWandNetwork.OpenPacket packet) {
        Minecraft.getInstance().setScreen(new TownWandConfigScreen(packet));
    }
}
