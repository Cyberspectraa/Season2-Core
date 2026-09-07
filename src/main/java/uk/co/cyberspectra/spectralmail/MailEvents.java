package uk.co.cyberspectra.spectralmail;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server lifecycle, login routing and reduced-frequency backend ticking. */
@Mod.EventBusSubscriber(modid = SpectralMail.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MailEvents {
    private MailEvents() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        SpectralMailConfig.reload();
        MailSavedData data = MailSavedData.get(server);
        KnownPlayerImporter.importUserCache(data);
        CourierManager.reset();
        CourierManager.queuePersistent(server, data);
        MailCooldowns.clear();
        DiscordService.start();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MailSavedData data = MailSavedData.get(player.m_20194_());
            data.remember(player);
            MailDelivery.routePending(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        MailSavedData data = MailSavedData.get(server);
        CourierManager.tick(server);
        DiscordService.tick(server, data);
        MailCooldowns.cleanup(System.currentTimeMillis());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DiscordService.stop();
        CourierManager.reset();
        MailCooldowns.clear();
    }
}
