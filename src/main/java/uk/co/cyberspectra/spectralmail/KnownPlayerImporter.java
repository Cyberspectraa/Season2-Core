package uk.co.cyberspectra.spectralmail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Imports vanilla's usercache.json once per server start to improve offline recipient discovery. */
public final class KnownPlayerImporter {
    private KnownPlayerImporter() {}

    public static int importUserCache(MailSavedData data) {
        if (data == null) return 0;
        Path path = Paths.get("usercache.json");
        if (!Files.isRegularFile(path)) return 0;
        int imported = 0;
        try {
            JsonElement root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return 0;
            JsonArray array = root.getAsJsonArray();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                if (!object.has("name") || !object.has("uuid")) continue;
                String name = object.get("name").getAsString();
                UUID uuid = UUID.fromString(object.get("uuid").getAsString());
                data.remember(uuid, name);
                imported++;
            }
        } catch (Throwable ignored) {
        }
        return imported;
    }
}
