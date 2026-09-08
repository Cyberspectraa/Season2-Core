package com.season2.townlife.runtime;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Keeps specialised Season 2 NPCs out of the generic Town Life scheduler. */
public final class Season2NpcProtection {
    private static final String MAIL_DATA_CLASS = "uk.co.cyberspectra.spectralmail.MailSavedData";

    private Season2NpcProtection() {}

    public static Optional<String> protectedReason(ServerLevel level, Mob mob) {
        if (level == null || mob == null) return Optional.empty();
        if (isBoundSpectralCourier(level.getServer(), mob.getUUID())) {
            return Optional.of("This EasyNPC is the bound Spectral Mail courier and is managed by the postal system.");
        }
        return Optional.empty();
    }

    public static boolean isProtected(ServerLevel level, Mob mob) {
        return protectedReason(level, mob).isPresent();
    }

    private static boolean isBoundSpectralCourier(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return false;
        try {
            Class<?> dataClass = Class.forName(MAIL_DATA_CLASS);
            Method get = dataClass.getMethod("get", MinecraftServer.class);
            Object data = get.invoke(null, server);
            if (data == null) return false;
            Object courierUuid = dataClass.getMethod("courierUuid").invoke(data);
            return uuid.equals(courierUuid);
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }
}
