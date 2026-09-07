package uk.co.cyberspectra.spectralmail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small server-owned configuration reader. It deliberately has no Forge-config dependency so the
 * Discord/courier backend never needs to load on the client beyond harmless class metadata.
 */
public final class SpectralMailConfig {
    private static final Path CONFIG_PATH = Paths.get("config", "spectralmail-server.toml");
    private static volatile SpectralMailConfig CURRENT = defaults();

    public final int maxMessageLength;
    public final int sendCooldownSeconds;
    public final int dropBoxCapacity;
    public final int letterBoxCapacity;

    public final boolean courierEnabled;
    public final double courierMaxDistance;
    public final double courierSpeed;
    public final double courierArrivalRadius;
    public final double courierPostalSpeed;
    public final double courierPostalArrivalRadius;
    public final int courierApproachRetrySeconds;
    public final int courierTimeoutSeconds;
    public final int courierReturnTimeoutSeconds;
    public final boolean courierTeleportHomeOnReturnTimeout;
    public final int courierWaitAfterDeliveryTicks;

    public final boolean discordEnabled;
    public final String discordToken;
    public final String discordGuildId;
    public final String discordPostOfficeChannelId;
    public final Set<String> discordAllowedRoleIds;
    public final String discordLogChannelId;
    public final boolean discordLogMessageContent;

    private SpectralMailConfig(Map<String, String> values) {
        this.maxMessageLength = boundedInt(values, "mail.max_message_length", 2000, 32, 12000);
        this.sendCooldownSeconds = boundedInt(values, "mail.send_cooldown_seconds", 5, 0, 3600);
        this.dropBoxCapacity = boundedInt(values, "postal.drop_box_capacity", 64, 1, 512);
        this.letterBoxCapacity = boundedInt(values, "postal.letter_box_capacity", 9, 1, 54);

        this.courierEnabled = bool(values, "courier.enabled", true);
        this.courierMaxDistance = boundedDouble(values, "courier.max_delivery_distance", 48.0D, 4.0D, 256.0D);
        double configuredCourierSpeed = boundedDouble(values, "courier.movement_speed", 0.82D, 0.1D, 2.5D);
        // Alpha.3/early-alpha.4 generated 1.05 as the stock value. Migrate only that untouched
        // legacy default automatically; custom speeds continue to be respected.
        boolean legacyStockSpeed = !values.containsKey("courier.postal_movement_speed")
                && Math.abs(configuredCourierSpeed - 1.05D) < 0.000001D;
        this.courierSpeed = legacyStockSpeed ? 0.82D : configuredCourierSpeed;
        this.courierArrivalRadius = boundedDouble(values, "courier.arrival_radius", 2.2D, 1.0D, 8.0D);
        this.courierPostalSpeed = boundedDouble(values, "courier.postal_movement_speed", 0.78D, 0.1D, 2.5D);
        this.courierPostalArrivalRadius = boundedDouble(values, "courier.postal_arrival_radius", 0.65D, 0.25D, 2.0D);
        this.courierApproachRetrySeconds = boundedInt(values, "courier.approach_retry_seconds", 4, 1, 30);
        this.courierTimeoutSeconds = boundedInt(values, "courier.delivery_timeout_seconds", 45, 5, 600);
        this.courierReturnTimeoutSeconds = boundedInt(values, "courier.return_timeout_seconds", 30, 5, 600);
        this.courierTeleportHomeOnReturnTimeout = bool(values, "courier.teleport_home_on_return_timeout", true);
        this.courierWaitAfterDeliveryTicks = boundedInt(values, "courier.wait_after_delivery_ticks", 40, 0, 400);

        this.discordEnabled = bool(values, "discord.enabled", false);
        this.discordToken = string(values, "discord.bot_token", "");
        this.discordGuildId = string(values, "discord.guild_id", "");
        this.discordPostOfficeChannelId = string(values, "discord.post_office_channel_id", "");
        this.discordAllowedRoleIds = Collections.unmodifiableSet(csvSet(string(values, "discord.allowed_role_ids", "")));
        this.discordLogChannelId = string(values, "discord.log_channel_id", "");
        this.discordLogMessageContent = bool(values, "discord.log_message_content", false);
    }

    public static SpectralMailConfig get() {
        return CURRENT;
    }

    public static synchronized SpectralMailConfig reload() {
        ensureTemplate();
        Map<String, String> values = new LinkedHashMap<>();
        String section = "";
        try {
            List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = stripComment(raw).trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim().toLowerCase(Locale.ROOT);
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) continue;
                String key = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
                String value = unquote(line.substring(eq + 1).trim());
                values.put(section.isEmpty() ? key : section + "." + key, value);
            }
        } catch (IOException ignored) {
            // Defaults remain safe. The next reload will retry the file.
        }
        CURRENT = new SpectralMailConfig(values);
        return CURRENT;
    }

    public String discordConnectionKey() {
        return discordEnabled + "|" + discordToken + "|" + discordGuildId + "|" + discordPostOfficeChannelId;
    }

    public boolean discordReadyToConnect() {
        return discordEnabled && !discordToken.isBlank();
    }

    public static Path configPath() {
        return CONFIG_PATH;
    }

    private static SpectralMailConfig defaults() {
        return new SpectralMailConfig(Collections.emptyMap());
    }

    private static void ensureTemplate() {
        if (Files.isRegularFile(CONFIG_PATH)) return;
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(CONFIG_PATH, TEMPLATE, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') quoted = !quoted;
            if (c == '#' && !quoted) return line.substring(0, i);
        }
        return line;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    private static String string(Map<String, String> values, String key, String fallback) {
        String value = values.get(key);
        return value == null ? fallback : value.trim();
    }

    private static boolean bool(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static int boundedInt(Map<String, String> values, String key, int fallback, int min, int max) {
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(string(values, key, Integer.toString(fallback)))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double boundedDouble(Map<String, String> values, String key, double fallback, double min, double max) {
        try {
            return Math.max(min, Math.min(max, Double.parseDouble(string(values, key, Double.toString(fallback)))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Set<String> csvSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    private static final String TEMPLATE = """
            # Spectral Mail server configuration
            # The Discord token is only read by the dedicated/server side backend. Never share this file.

            [mail]
            max_message_length = 2000
            send_cooldown_seconds = 5

            [postal]
            # Logical mail slots. Letter Box defaults to one vanilla inventory row.
            drop_box_capacity = 64
            letter_box_capacity = 9

            [courier]
            enabled = true
            max_delivery_distance = 48.0
            # 0.82 gives the EasyNPC a deliberate walking/postman pace.
            movement_speed = 0.82
            # Player deliveries can stop a little farther away; postal blocks use their own tighter service radius.
            arrival_radius = 2.2
            # Separate service speed applies even to older configs that still have movement_speed = 1.05.
            postal_movement_speed = 0.78
            postal_arrival_radius = 0.65
            # If the front service point makes no progress, try a front-corner approach.
            approach_retry_seconds = 4
            delivery_timeout_seconds = 45
            return_timeout_seconds = 30
            teleport_home_on_return_timeout = true
            wait_after_delivery_ticks = 40

            [discord]
            enabled = false
            bot_token = ""
            guild_id = ""
            post_office_channel_id = ""
            # Comma-separated Discord role IDs. Leave blank to allow everyone in the configured channel.
            allowed_role_ids = ""
            log_channel_id = ""
            log_message_content = false
            """;
}
