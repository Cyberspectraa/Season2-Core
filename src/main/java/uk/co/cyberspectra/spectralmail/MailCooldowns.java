package uk.co.cyberspectra.spectralmail;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-thread cooldown cache with periodic pruning. */
public final class MailCooldowns {
    private static final Map<UUID, Long> NEXT_SEND = new HashMap<>();
    private static long lastCleanup;

    private MailCooldowns() {}

    public static long remainingMillis(UUID sender, long now, int cooldownSeconds) {
        if (sender == null || cooldownSeconds <= 0) return 0L;
        long until = NEXT_SEND.getOrDefault(sender, 0L);
        return Math.max(0L, until - now);
    }

    public static void markSent(UUID sender, long now, int cooldownSeconds) {
        if (sender == null || cooldownSeconds <= 0) return;
        NEXT_SEND.put(sender, now + cooldownSeconds * 1000L);
    }

    public static void cleanup(long now) {
        if (now - lastCleanup < 60_000L) return;
        lastCleanup = now;
        Iterator<Map.Entry<UUID, Long>> iterator = NEXT_SEND.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() < now - 60_000L) iterator.remove();
        }
    }

    public static void clear() {
        NEXT_SEND.clear();
        lastCleanup = 0L;
    }
}
