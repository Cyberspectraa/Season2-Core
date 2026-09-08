package uk.co.cyberspectra.spectralmail;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Small server-only vanilla feedback helpers. No state mutation lives here. */
public final class PostalFeedback {
    private static final SoundSource BLOCKS = SoundSource.values()[4];

    private PostalFeedback() {}

    public static void posted(MinecraftServer server, MailSavedData.PostalAddress address) {
        ServerLevel level = serverLevel(server, address);
        if (level == null) return;
        BlockPos pos = pos(address);
        level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, BLOCKS, 0.65F, 1.05F);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, BLOCKS, 0.45F, 1.20F);
        particles(level, address, 0.85D);
    }

    public static void deliveredToLetterBox(MinecraftServer server, MailSavedData.PostalAddress address) {
        ServerLevel level = serverLevel(server, address);
        if (level == null) return;
        level.playSound(null, pos(address), SoundEvents.IRON_TRAPDOOR_CLOSE, BLOCKS, 0.55F, 0.95F);
        particles(level, address, 0.75D);
    }

    public static void collected(MinecraftServer server, MailSavedData.PostalAddress address) {
        ServerLevel level = serverLevel(server, address);
        if (level == null) return;
        level.playSound(null, pos(address), SoundEvents.BOOK_PAGE_TURN, BLOCKS, 0.60F, 0.95F);
        particles(level, address, 0.75D);
    }

    private static ServerLevel serverLevel(MinecraftServer server, MailSavedData.PostalAddress address) {
        Object level = address == null ? null : PostalRuntime.levelFor(server, address.dimension());
        return level instanceof ServerLevel serverLevel ? serverLevel : null;
    }

    private static BlockPos pos(MailSavedData.PostalAddress address) {
        return new BlockPos(address.x(), address.y(), address.z());
    }

    private static void particles(ServerLevel level, MailSavedData.PostalAddress address, double yOffset) {
        level.sendParticles(ParticleTypes.POOF,
                address.x() + 0.5D, address.y() + yOffset, address.z() + 0.5D,
                2, 0.12D, 0.08D, 0.12D, 0.01D);
    }
}
