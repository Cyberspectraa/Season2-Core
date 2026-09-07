package uk.co.cyberspectra.spectralmail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-only Discord Post Office using Discord's v10 REST/Gateway APIs and JDK networking only.
 * It requests the GUILDS intent, not Message Content. Buttons/selects/modals are handled as Discord
 * interactions and feed the exact same server-owned MailSavedData + courier delivery path.
 */
public final class DiscordService {
    private static final URI API = URI.create("https://discord.com/api/v10/");
    private static final URI GATEWAY = URI.create("wss://gateway.discord.gg/?v=10&encoding=json");

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final ConcurrentLinkedQueue<InboundMail> INBOUND = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<String> PANEL_ID_UPDATES = new ConcurrentLinkedQueue<>();
    private static volatile List<PlayerChoice> PLAYER_CHOICES = List.of();

    private static volatile WebSocket socket;
    private static volatile ScheduledExecutorService scheduler;
    private static volatile ExecutorService ioExecutor;
    private static volatile ScheduledFuture<?> heartbeatFuture;
    private static volatile boolean running;
    private static volatile boolean connected;
    private static volatile long sequence = -1L;
    private static volatile String applicationId = "";
    private static volatile String botName = "";
    private static volatile String lastError = "";
    private static volatile boolean guildVerified;
    private static volatile boolean channelVerified;
    private static volatile String connectionKey = "";
    private static final AtomicBoolean reconnectPending = new AtomicBoolean();
    private static long ticks;

    private DiscordService() {}

    public static synchronized void start() {
        SpectralMailConfig config = SpectralMailConfig.get();
        if (!config.discordReadyToConnect()) {
            stop();
            return;
        }
        String key = config.discordConnectionKey();
        if (running && key.equals(connectionKey)) return;
        stop();
        connectionKey = key;
        running = true;
        ioExecutor = Executors.newSingleThreadExecutor(r -> daemon(r, "SpectralMail-Discord-IO"));
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "SpectralMail-Discord-Heartbeat"));
        connectGateway();
        verifyConfiguredTargets();
    }

    public static synchronized void reload() {
        String previousKey = connectionKey;
        SpectralMailConfig config = SpectralMailConfig.reload();
        String nextKey = config.discordConnectionKey();
        if (!nextKey.equals(previousKey)) {
            start();
        } else if (config.discordReadyToConnect() && !running) {
            start();
        }
    }

    public static synchronized void stop() {
        running = false;
        connected = false;
        sequence = -1L;
        reconnectPending.set(false);
        WebSocket currentSocket = socket;
        socket = null;
        if (currentSocket != null) {
            try { currentSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Server stopping"); } catch (Throwable ignored) {}
        }
        ScheduledFuture<?> heartbeat = heartbeatFuture;
        heartbeatFuture = null;
        if (heartbeat != null) heartbeat.cancel(true);
        ScheduledExecutorService currentScheduler = scheduler;
        scheduler = null;
        if (currentScheduler != null) currentScheduler.shutdownNow();
        ExecutorService currentIo = ioExecutor;
        ioExecutor = null;
        if (currentIo != null) currentIo.shutdownNow();
        botName = "";
        applicationId = "";
        guildVerified = false;
        channelVerified = false;
    }

    public static void tick(MinecraftServer server, MailSavedData data) {
        if (server == null || data == null) return;
        ticks++;
        if (ticks % 20L == 0L) {
            List<PlayerChoice> choices = new ArrayList<>();
            for (MailSavedData.KnownPlayer known : data.knownPlayersAlphabetical()) {
                choices.add(new PlayerChoice(known.uuid(), known.name()));
            }
            PLAYER_CHOICES = List.copyOf(choices);
        }

        String panelId;
        while ((panelId = PANEL_ID_UPDATES.poll()) != null) data.setDiscordPanelMessageId(panelId);

        int processed = 0;
        InboundMail inbound;
        while (processed++ < 20 && (inbound = INBOUND.poll()) != null) {
            handleInboundOnServer(server, data, inbound);
        }
    }

    public static boolean ensurePanel(MailSavedData data) {
        SpectralMailConfig config = SpectralMailConfig.get();
        if (!config.discordReadyToConnect() || config.discordPostOfficeChannelId.isBlank()) {
            lastError = "Discord is disabled, has no token, or has no Post Office channel configured.";
            return false;
        }
        ExecutorService executor = ioExecutor;
        if (executor == null) {
            lastError = "Discord backend is not running.";
            return false;
        }
        String existingId = data == null ? "" : data.discordPanelMessageId();
        executor.execute(() -> postOrRefreshPanel(existingId));
        return true;
    }

    public static List<String> statusLines(MailSavedData data) {
        SpectralMailConfig config = SpectralMailConfig.get();
        List<String> lines = new ArrayList<>();
        lines.add("Discord: " + (connected ? "connected" : running ? "connecting" : "disabled/stopped") + ".");
        lines.add("Bot: " + (botName.isBlank() ? "not identified" : botName) + ". Token: " + (config.discordToken.isBlank() ? "not configured" : "configured") + ".");
        lines.add("Guild: " + displayConfigured(config.discordGuildId, guildVerified) + ". Channel: " + displayConfigured(config.discordPostOfficeChannelId, channelVerified) + ".");
        lines.add("Known Minecraft players available to Discord: " + (data == null ? PLAYER_CHOICES.size() : data.knownPlayerCount()) + ".");
        lines.add("Post Office panel: " + (data != null && !data.discordPanelMessageId().isBlank() ? "tracked" : "not created yet") + ".");
        if (!lastError.isBlank()) lines.add("Last Discord error: " + lastError);
        return lines;
    }

    private static String displayConfigured(String id, boolean verified) {
        if (id == null || id.isBlank()) return "not configured";
        return verified ? "configured/verified" : "configured/unverified";
    }

    private static void handleInboundOnServer(MinecraftServer server, MailSavedData data, InboundMail inbound) {
        String recipientName = data.canonicalName(inbound.recipientUuid);
        if (recipientName == null || recipientName.isBlank()) {
            editOriginal(inbound.applicationId, inbound.interactionToken, "That Minecraft player is no longer known to the Post Office.");
            return;
        }

        String message = inbound.message == null ? "" : inbound.message.trim();
        int max = SpectralMailConfig.get().maxMessageLength;
        if (message.isEmpty() || message.length() > max) {
            editOriginal(inbound.applicationId, inbound.interactionToken,
                    message.isEmpty() ? "The letter was empty." : "That letter is longer than the server limit of " + max + " characters.");
            return;
        }

        UUID senderUuid = UUID.nameUUIDFromBytes(("spectralmail:discord:" + inbound.discordUserId).getBytes(StandardCharsets.UTF_8));
        MailRecord record = new MailRecord(
                UUID.randomUUID().toString(),
                senderUuid,
                "Discord: " + inbound.discordDisplayName,
                inbound.recipientUuid,
                recipientName,
                message,
                System.currentTimeMillis(),
                MailRecord.PENDING
        );
        data.add(record);

        ServerPlayer online = MinecraftRuntime.findOnlinePlayer(server, record.recipientUuid);
        MailDelivery.DeliveryResult result = MailDelivery.route(data, record, online);

        String confirmation = switch (result) {
            case DELIVERED -> "Your letter was delivered to " + recipientName + ".";
            case QUEUED_FOR_COURIER -> "Your letter was accepted. The postman is taking it to " + recipientName + ".";
            case PENDING -> "Your letter was accepted and is waiting safely for " + recipientName + ".";
        };
        editOriginal(inbound.applicationId, inbound.interactionToken, confirmation);
        logDelivery(record, inbound.discordDisplayName);
    }

    private static void connectGateway() {
        if (!running) return;
        try {
            HTTP.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .buildAsync(GATEWAY, new GatewayListener())
                    .whenComplete((ws, error) -> {
                        if (error != null) {
                            setError("Gateway connection failed: " + safeMessage(error));
                            scheduleReconnect();
                        } else {
                            socket = ws;
                        }
                    });
        } catch (Throwable error) {
            setError("Gateway connection failed: " + safeMessage(error));
            scheduleReconnect();
        }
    }

    private static void identify() {
        SpectralMailConfig config = SpectralMailConfig.get();
        WebSocket ws = socket;
        if (ws == null || !running) return;

        JsonObject properties = new JsonObject();
        properties.addProperty("os", System.getProperty("os.name", "unknown"));
        properties.addProperty("browser", "spectralmail");
        properties.addProperty("device", "spectralmail");

        JsonObject d = new JsonObject();
        d.addProperty("token", config.discordToken);
        d.addProperty("intents", 1); // GUILDS only; no MESSAGE_CONTENT intent
        d.add("properties", properties);

        JsonObject root = new JsonObject();
        root.addProperty("op", 2);
        root.add("d", d);
        ws.sendText(root.toString(), true);
    }

    private static void scheduleHeartbeat(long intervalMillis) {
        ScheduledExecutorService current = scheduler;
        if (current == null) return;
        ScheduledFuture<?> previous = heartbeatFuture;
        if (previous != null) previous.cancel(false);
        heartbeatFuture = current.scheduleAtFixedRate(() -> {
            WebSocket ws = socket;
            if (!running || ws == null) return;
            JsonObject beat = new JsonObject();
            beat.addProperty("op", 1);
            if (sequence >= 0) beat.addProperty("d", sequence);
            else beat.add("d", null);
            ws.sendText(beat.toString(), true);
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    private static void handleGatewayPayload(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.has("s") && !root.get("s").isJsonNull()) sequence = root.get("s").getAsLong();
            int op = root.get("op").getAsInt();
            if (op == 10) {
                long heartbeat = root.getAsJsonObject("d").get("heartbeat_interval").getAsLong();
                scheduleHeartbeat(heartbeat);
                identify();
                return;
            }
            if (op == 7 || op == 9) {
                connected = false;
                scheduleReconnect();
                return;
            }
            if (op != 0) return;

            String event = root.has("t") && !root.get("t").isJsonNull() ? root.get("t").getAsString() : "";
            JsonObject d = root.getAsJsonObject("d");
            if ("READY".equals(event)) {
                connected = true;
                reconnectPending.set(false);
                JsonObject user = d.getAsJsonObject("user");
                botName = user == null ? "" : text(user, "username");
                JsonObject application = d.getAsJsonObject("application");
                if (application != null) applicationId = text(application, "id");
                lastError = "";
            } else if ("INTERACTION_CREATE".equals(event)) {
                handleInteraction(d);
            }
        } catch (Throwable error) {
            setError("Gateway payload error: " + safeMessage(error));
        }
    }

    private static void handleInteraction(JsonObject interaction) {
        if (interaction == null) return;
        String id = text(interaction, "id");
        String token = text(interaction, "token");
        String appId = text(interaction, "application_id");
        if (!appId.isBlank()) applicationId = appId;

        SpectralMailConfig config = SpectralMailConfig.get();
        String channelId = text(interaction, "channel_id");
        if (!config.discordPostOfficeChannelId.isBlank() && !config.discordPostOfficeChannelId.equals(channelId)) {
            respondEphemeral(id, token, "Spectral Mail interactions are restricted to the configured Post Office channel.");
            return;
        }
        if (!hasAllowedRole(interaction, config.discordAllowedRoleIds)) {
            respondEphemeral(id, token, "You do not have a role permitted to use the Post Office.");
            return;
        }

        int interactionType = integer(interaction, "type", 0);
        JsonObject data = interaction.getAsJsonObject("data");
        if (data == null) return;
        String customId = text(data, "custom_id");

        if (interactionType == 3 && "sm_write".equals(customId)) {
            respondPlayerPicker(id, token, 0, false);
            return;
        }
        if (interactionType == 3 && customId.startsWith("sm_page:")) {
            int page = parseInt(customId.substring("sm_page:".length()), 0);
            respondPlayerPicker(id, token, page, true);
            return;
        }
        if (interactionType == 3 && "sm_recipient".equals(customId)) {
            JsonArray values = data.getAsJsonArray("values");
            if (values == null || values.size() == 0) {
                respondEphemeral(id, token, "No Minecraft player was selected.");
                return;
            }
            String uuidText = values.get(0).getAsString();
            PlayerChoice choice = choiceByUuid(uuidText);
            if (choice == null) {
                respondEphemeral(id, token, "That Minecraft player is no longer in the current Post Office list.");
                return;
            }
            respondModal(id, token, choice);
            return;
        }
        if (interactionType == 5 && customId.startsWith("sm_modal:")) {
            UUID recipient;
            try { recipient = UUID.fromString(customId.substring("sm_modal:".length())); }
            catch (IllegalArgumentException error) {
                respondEphemeral(id, token, "The selected Minecraft recipient was invalid.");
                return;
            }
            String message = modalValue(data, "message");
            String userId = discordUserId(interaction);
            String displayName = discordDisplayName(interaction);
            deferEphemeral(id, token);
            INBOUND.add(new InboundMail(appId.isBlank() ? applicationId : appId, token, userId, displayName, recipient, message));
        }
    }

    private static boolean hasAllowedRole(JsonObject interaction, Set<String> allowed) {
        if (allowed == null || allowed.isEmpty()) return true;
        JsonObject member = interaction.getAsJsonObject("member");
        if (member == null) return false;
        JsonArray roles = member.getAsJsonArray("roles");
        if (roles == null) return false;
        for (JsonElement role : roles) if (allowed.contains(role.getAsString())) return true;
        return false;
    }

    private static void respondPlayerPicker(String id, String token, int requestedPage, boolean update) {
        List<PlayerChoice> choices = PLAYER_CHOICES;
        if (choices.isEmpty()) {
            if (update) respondUpdate(id, token, textData("No Minecraft players are known to the Post Office yet."));
            else respondEphemeral(id, token, "No Minecraft players are known to the Post Office yet.");
            return;
        }

        int pages = Math.max(1, (choices.size() + 24) / 25);
        int page = Math.max(0, Math.min(pages - 1, requestedPage));
        int from = page * 25;
        int to = Math.min(choices.size(), from + 25);

        JsonObject select = new JsonObject();
        select.addProperty("type", 3);
        select.addProperty("custom_id", "sm_recipient");
        select.addProperty("placeholder", "Choose a Minecraft recipient");
        select.addProperty("min_values", 1);
        select.addProperty("max_values", 1);
        JsonArray options = new JsonArray();
        for (int i = from; i < to; i++) {
            PlayerChoice choice = choices.get(i);
            JsonObject option = new JsonObject();
            option.addProperty("label", truncate(choice.name, 100));
            option.addProperty("value", choice.uuid.toString());
            options.add(option);
        }
        select.add("options", options);
        JsonObject selectRow = new JsonObject();
        selectRow.addProperty("type", 1);
        JsonArray selectComponents = new JsonArray();
        selectComponents.add(select);
        selectRow.add("components", selectComponents);

        JsonArray rows = new JsonArray();
        rows.add(selectRow);
        if (pages > 1) {
            JsonArray nav = new JsonArray();
            if (page > 0) nav.add(button("Previous", "sm_page:" + (page - 1), 2));
            if (page + 1 < pages) nav.add(button("Next", "sm_page:" + (page + 1), 2));
            JsonObject navRow = new JsonObject();
            navRow.addProperty("type", 1);
            navRow.add("components", nav);
            rows.add(navRow);
        }

        JsonObject data = new JsonObject();
        data.addProperty("content", "Choose a Minecraft recipient. Page " + (page + 1) + " / " + pages + ".");
        if (!update) data.addProperty("flags", 64);
        data.add("components", rows);
        if (update) respondUpdate(id, token, data);
        else respond(id, token, 4, data);
    }

    private static void respondModal(String id, String token, PlayerChoice choice) {
        JsonObject input = new JsonObject();
        input.addProperty("type", 4);
        input.addProperty("custom_id", "message");
        input.addProperty("style", 2);
        input.addProperty("label", "Letter to " + truncate(choice.name, 75));
        input.addProperty("placeholder", "Write your letter...");
        input.addProperty("required", true);
        input.addProperty("max_length", Math.min(4000, SpectralMailConfig.get().maxMessageLength));

        JsonArray inner = new JsonArray();
        inner.add(input);
        JsonObject row = new JsonObject();
        row.addProperty("type", 1);
        row.add("components", inner);
        JsonArray rows = new JsonArray();
        rows.add(row);

        JsonObject data = new JsonObject();
        data.addProperty("custom_id", "sm_modal:" + choice.uuid);
        data.addProperty("title", "Write a Letter");
        data.add("components", rows);
        respond(id, token, 9, data);
    }

    private static void respondEphemeral(String id, String token, String message) {
        JsonObject data = textData(message);
        data.addProperty("flags", 64);
        respond(id, token, 4, data);
    }

    private static void deferEphemeral(String id, String token) {
        JsonObject data = new JsonObject();
        data.addProperty("flags", 64);
        respond(id, token, 5, data);
    }

    private static void respondUpdate(String id, String token, JsonObject data) {
        respond(id, token, 7, data);
    }

    private static void respond(String id, String token, int type, JsonObject data) {
        if (id.isBlank() || token.isBlank()) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("type", type);
        if (data != null) payload.add("data", data);
        sendAsyncNoAuth("interactions/" + id + "/" + token + "/callback", "POST", payload.toString());
    }

    private static void editOriginal(String appId, String interactionToken, String content) {
        if (appId == null || appId.isBlank() || interactionToken == null || interactionToken.isBlank()) return;
        JsonObject body = textData(content);
        sendAsyncNoAuth("webhooks/" + appId + "/" + interactionToken + "/messages/@original", "PATCH", body.toString());
    }

    private static void postOrRefreshPanel(String existingId) {
        SpectralMailConfig config = SpectralMailConfig.get();
        String channel = config.discordPostOfficeChannelId;
        if (channel.isBlank()) return;
        String body = panelPayload().toString();
        try {
            if (existingId != null && !existingId.isBlank()) {
                HttpResponse<String> response = request("PATCH", "channels/" + channel + "/messages/" + existingId, body, true);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    channelVerified = true;
                    return;
                }
            }
            HttpResponse<String> response = request("POST", "channels/" + channel + "/messages", body, true);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                String id = text(json, "id");
                if (!id.isBlank()) PANEL_ID_UPDATES.add(id);
                channelVerified = true;
                lastError = "";
            } else {
                setError("Could not create Discord Post Office panel (HTTP " + response.statusCode() + ").");
            }
        } catch (Throwable error) {
            setError("Could not create Discord Post Office panel: " + safeMessage(error));
        }
    }

    private static JsonObject panelPayload() {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Spectral Post Office");
        embed.addProperty("description", "Send a sealed letter to a known Minecraft player. The Post Office stores it safely and uses the same in-game delivery system as Minecraft mail.");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonArray components = new JsonArray();
        components.add(button("Write a Letter", "sm_write", 1));
        JsonObject row = new JsonObject();
        row.addProperty("type", 1);
        row.add("components", components);
        JsonArray rows = new JsonArray();
        rows.add(row);

        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);
        payload.add("components", rows);
        return payload;
    }

    private static JsonObject button(String label, String customId, int style) {
        JsonObject button = new JsonObject();
        button.addProperty("type", 2);
        button.addProperty("style", style);
        button.addProperty("label", label);
        button.addProperty("custom_id", customId);
        return button;
    }

    private static void verifyConfiguredTargets() {
        ExecutorService executor = ioExecutor;
        if (executor == null) return;
        executor.execute(() -> {
            SpectralMailConfig config = SpectralMailConfig.get();
            try {
                if (!config.discordGuildId.isBlank()) {
                    guildVerified = request("GET", "guilds/" + config.discordGuildId, null, true).statusCode() == 200;
                }
                if (!config.discordPostOfficeChannelId.isBlank()) {
                    channelVerified = request("GET", "channels/" + config.discordPostOfficeChannelId, null, true).statusCode() == 200;
                }
            } catch (Throwable error) {
                setError("Discord configuration check failed: " + safeMessage(error));
            }
        });
    }

    private static void logDelivery(MailRecord record, String discordDisplayName) {
        SpectralMailConfig config = SpectralMailConfig.get();
        if (config.discordLogChannelId.isBlank()) return;
        ExecutorService executor = ioExecutor;
        if (executor == null) return;
        executor.execute(() -> {
            StringBuilder text = new StringBuilder();
            text.append("Spectral Mail: Discord sender **").append(discordDisplayName)
                    .append("** -> Minecraft **").append(record.recipientName)
                    .append("** | mail ID `").append(record.id).append('`');
            if (config.discordLogMessageContent) text.append("\nContent: ").append(record.message);
            JsonObject payload = new JsonObject();
            payload.addProperty("content", truncate(text.toString(), 1900));
            try {
                request("POST", "channels/" + config.discordLogChannelId + "/messages", payload.toString(), true);
            } catch (Throwable error) {
                setError("Discord delivery log failed: " + safeMessage(error));
            }
        });
    }

    private static HttpResponse<String> request(String method, String path, String body, boolean auth) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(API.resolve(path))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "SpectralMail/1.0")
                .header("Content-Type", "application/json");
        if (auth) builder.header("Authorization", "Bot " + SpectralMailConfig.get().discordToken);
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void sendAsyncNoAuth(String path, String method, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(API.resolve(path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> {
                        setError("Discord interaction response failed: " + safeMessage(error));
                        return null;
                    });
        } catch (Throwable error) {
            setError("Discord interaction response failed: " + safeMessage(error));
        }
    }

    private static void scheduleReconnect() {
        if (!running || !reconnectPending.compareAndSet(false, true)) return;
        ScheduledExecutorService current = scheduler;
        if (current == null) return;
        current.schedule(() -> {
            reconnectPending.set(false);
            if (!running) return;
            connected = false;
            ScheduledFuture<?> heartbeat = heartbeatFuture;
            heartbeatFuture = null;
            if (heartbeat != null) heartbeat.cancel(false);
            WebSocket old = socket;
            socket = null;
            if (old != null) try { old.abort(); } catch (Throwable ignored) {}
            connectGateway();
        }, 5, TimeUnit.SECONDS);
    }

    private static PlayerChoice choiceByUuid(String uuidText) {
        for (PlayerChoice choice : PLAYER_CHOICES) if (choice.uuid.toString().equals(uuidText)) return choice;
        return null;
    }

    private static String modalValue(JsonObject data, String wantedId) {
        JsonArray rows = data.getAsJsonArray("components");
        if (rows == null) return "";
        for (JsonElement rowElement : rows) {
            if (!rowElement.isJsonObject()) continue;
            JsonArray components = rowElement.getAsJsonObject().getAsJsonArray("components");
            if (components == null) continue;
            for (JsonElement componentElement : components) {
                if (!componentElement.isJsonObject()) continue;
                JsonObject component = componentElement.getAsJsonObject();
                if (wantedId.equals(text(component, "custom_id"))) return text(component, "value");
            }
        }
        return "";
    }

    private static String discordUserId(JsonObject interaction) {
        JsonObject user = interactionUser(interaction);
        return user == null ? "unknown" : text(user, "id");
    }

    private static String discordDisplayName(JsonObject interaction) {
        JsonObject user = interactionUser(interaction);
        if (user == null) return "Unknown";
        String global = text(user, "global_name");
        return global.isBlank() ? text(user, "username") : global;
    }

    private static JsonObject interactionUser(JsonObject interaction) {
        JsonObject member = interaction.getAsJsonObject("member");
        if (member != null && member.getAsJsonObject("user") != null) return member.getAsJsonObject("user");
        return interaction.getAsJsonObject("user");
    }

    private static JsonObject textData(String text) {
        JsonObject data = new JsonObject();
        data.addProperty("content", text == null ? "" : text);
        return data;
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); } catch (RuntimeException ignored) { return ""; }
    }

    private static int integer(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return fallback;
        try { return object.get(key).getAsInt(); } catch (RuntimeException ignored) { return fallback; }
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return fallback; }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static void setError(String message) {
        lastError = truncate(message == null ? "Unknown Discord error" : message, 240);
    }

    private static String safeMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static final class GatewayListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            socket = webSocket;
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String payload = buffer.toString();
                buffer.setLength(0);
                handleGatewayPayload(payload);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connected = false;
            if (running) scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connected = false;
            setError("Gateway error: " + safeMessage(error));
            if (running) scheduleReconnect();
        }
    }

    private record PlayerChoice(UUID uuid, String name) {}
    private record InboundMail(String applicationId, String interactionToken, String discordUserId,
                               String discordDisplayName, UUID recipientUuid, String message) {}
}
