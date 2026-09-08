package uk.co.cyberspectra.spectralmail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-persistent authoritative mailbox database. Alpha.3 preserves alpha.1/alpha.2 tags and adds
 * physical Drop Box queues plus one registered Letter Box address per player.
 */
public final class MailSavedData extends SavedData {
    private static final String DATA_ID = "spectralmail_mail";
    private static final int COMPOUND_TAG_ID = 10;

    private final Map<String, MailRecord> records = new HashMap<>();
    private final Map<String, UUID> knownPlayers = new HashMap<>();
    private final Map<UUID, String> canonicalNames = new HashMap<>();

    private final Map<UUID, List<String>> recipientIndex = new HashMap<>();
    private final Map<UUID, LinkedHashSet<String>> pendingIndex = new HashMap<>();
    private final Map<UUID, Integer> unreadCounts = new HashMap<>();
    private List<String> cachedAlphabeticalNames = List.of();
    private boolean namesCacheDirty = true;

    // Physical postal state (all additive to the older save format).
    private final Map<UUID, PostalAddress> letterBoxes = new HashMap<>();
    private final Map<UUID, List<String>> letterBoxContents = new HashMap<>();
    private final Map<String, UUID> boxedOwnerByMail = new HashMap<>();

    private final Map<String, PostalAddress> dropBoxes = new HashMap<>();
    private final Map<String, List<String>> dropBoxQueues = new HashMap<>();
    private final Map<String, PostalAddress> dropBoxByMail = new HashMap<>();

    private UUID courierUuid;
    private boolean courierHomeSet;
    private double courierHomeX;
    private double courierHomeY;
    private double courierHomeZ;
    private String discordPanelMessageId = "";

    public static MailSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MailSavedData::load, MailSavedData::new, DATA_ID);
    }

    public static MailSavedData load(CompoundTag root) {
        MailSavedData data = new MailSavedData();

        ListTag known = root.getList("KnownPlayers", COMPOUND_TAG_ID);
        for (int i = 0; i < known.size(); i++) {
            CompoundTag entry = known.getCompound(i);
            if (!entry.hasUUID("UUID")) continue;
            UUID uuid = entry.getUUID("UUID");
            String name = entry.getString("Name");
            if (name == null || name.isBlank()) continue;
            data.knownPlayers.put(name.toLowerCase(Locale.ROOT), uuid);
            data.canonicalNames.put(uuid, name);
        }

        ListTag mail = root.getList("Mail", COMPOUND_TAG_ID);
        for (int i = 0; i < mail.size(); i++) {
            MailRecord record = MailRecord.load(mail.getCompound(i));
            if (record != null) data.records.put(record.id, record);
        }

        if (root.hasUUID("CourierUUID")) data.courierUuid = root.getUUID("CourierUUID");
        String home = root.getString("CourierHome");
        if (home != null && !home.isBlank()) {
            String[] parts = home.split(",", -1);
            if (parts.length == 3) {
                try {
                    data.courierHomeX = Double.parseDouble(parts[0]);
                    data.courierHomeY = Double.parseDouble(parts[1]);
                    data.courierHomeZ = Double.parseDouble(parts[2]);
                    data.courierHomeSet = true;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        data.discordPanelMessageId = root.getString("DiscordPanelMessageId");
        if (data.discordPanelMessageId == null) data.discordPanelMessageId = "";

        ListTag boxes = root.getList("LetterBoxes", COMPOUND_TAG_ID);
        for (int i = 0; i < boxes.size(); i++) {
            CompoundTag tag = boxes.getCompound(i);
            if (!tag.hasUUID("Owner")) continue;
            UUID owner = tag.getUUID("Owner");
            PostalAddress address = PostalAddress.load(tag);
            if (owner == null || address == null) continue;
            data.letterBoxes.put(owner, address);
            List<String> contents = parseIds(tag.getString("Contents"));
            if (!contents.isEmpty()) data.letterBoxContents.put(owner, new ArrayList<>(contents));
        }

        ListTag drops = root.getList("DropBoxes", COMPOUND_TAG_ID);
        for (int i = 0; i < drops.size(); i++) {
            CompoundTag tag = drops.getCompound(i);
            PostalAddress address = PostalAddress.load(tag);
            if (address == null) continue;
            data.dropBoxes.put(address.key(), address);
            List<String> queue = parseIds(tag.getString("Queue"));
            if (!queue.isEmpty()) data.dropBoxQueues.put(address.key(), new ArrayList<>(queue));
        }

        data.rebuildIndexes();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root) {
        ListTag known = new ListTag();
        for (Map.Entry<UUID, String> entry : canonicalNames.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", entry.getKey());
            tag.putString("Name", entry.getValue());
            known.add(tag);
        }
        root.put("KnownPlayers", known);

        ListTag mail = new ListTag();
        for (MailRecord record : records.values()) mail.add(record.save());
        root.put("Mail", mail);

        if (courierUuid != null) root.putUUID("CourierUUID", courierUuid);
        if (courierHomeSet) root.putString("CourierHome", courierHomeX + "," + courierHomeY + "," + courierHomeZ);
        if (discordPanelMessageId != null && !discordPanelMessageId.isBlank()) {
            root.putString("DiscordPanelMessageId", discordPanelMessageId);
        }

        ListTag boxes = new ListTag();
        for (Map.Entry<UUID, PostalAddress> entry : letterBoxes.entrySet()) {
            CompoundTag tag = entry.getValue().save();
            tag.putUUID("Owner", entry.getKey());
            List<String> contents = letterBoxContents.get(entry.getKey());
            if (contents != null && !contents.isEmpty()) tag.putString("Contents", String.join(",", contents));
            boxes.add(tag);
        }
        root.put("LetterBoxes", boxes);

        ListTag drops = new ListTag();
        for (PostalAddress address : dropBoxes.values()) {
            CompoundTag tag = address.save();
            List<String> queue = dropBoxQueues.get(address.key());
            if (queue != null && !queue.isEmpty()) tag.putString("Queue", String.join(",", queue));
            drops.add(tag);
        }
        root.put("DropBoxes", drops);
        return root;
    }

    public void remember(ServerPlayer player) {
        if (player != null) remember(player.getUUID(), player.getName().getString());
    }

    public void remember(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;
        String clean = name.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        UUID oldUuid = knownPlayers.put(lower, uuid);
        String oldName = canonicalNames.put(uuid, clean);
        if (oldName != null && !oldName.equalsIgnoreCase(clean)) knownPlayers.remove(oldName.toLowerCase(Locale.ROOT), uuid);
        if (!uuid.equals(oldUuid) || !clean.equals(oldName)) {
            namesCacheDirty = true;
            setDirty();
        }
    }

    public UUID findKnownPlayer(String name) { return name == null ? null : knownPlayers.get(name.toLowerCase(Locale.ROOT)); }
    public String canonicalName(UUID uuid) { return canonicalNames.get(uuid); }
    public int knownPlayerCount() { return canonicalNames.size(); }

    public List<String> alphabeticalKnownNames() {
        if (namesCacheDirty) {
            List<String> names = new ArrayList<>(canonicalNames.values());
            names.sort(String.CASE_INSENSITIVE_ORDER);
            cachedAlphabeticalNames = Collections.unmodifiableList(names);
            namesCacheDirty = false;
        }
        return cachedAlphabeticalNames;
    }

    public List<KnownPlayer> knownPlayersAlphabetical() {
        List<KnownPlayer> result = new ArrayList<>();
        for (String name : alphabeticalKnownNames()) {
            UUID uuid = findKnownPlayer(name);
            if (uuid != null) result.add(new KnownPlayer(uuid, name));
        }
        return result;
    }

    public void add(MailRecord record) {
        if (record == null || record.id == null || record.id.isBlank()) return;
        MailRecord previous = records.put(record.id, record);
        if (previous != null) removeFromIndexes(previous);
        addToIndexes(record);
        setDirty();
    }

    public MailRecord getRecord(String id) { return records.get(id); }

    public void removeRecord(MailRecord record) {
        if (record == null || records.remove(record.id) == null) return;
        removeFromDropBoxRef(record.id);
        removeFromLetterBoxRef(record.id);
        removeFromIndexes(record);
        setDirty();
    }

    public List<MailRecord> pendingFor(UUID recipient) {
        Set<String> ids = pendingIndex.get(recipient);
        if (ids == null || ids.isEmpty()) return List.of();
        List<MailRecord> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            MailRecord record = records.get(id);
            if (record != null && record.state == MailRecord.PENDING) result.add(record);
        }
        result.sort((a, b) -> Long.compare(a.sentAt, b.sentAt));
        return result;
    }

    public List<MailRecord> forRecipient(UUID recipient) {
        List<String> ids = recipientIndex.get(recipient);
        if (ids == null || ids.isEmpty()) return List.of();
        List<MailRecord> result = new ArrayList<>(ids.size());
        for (String id : ids) {
            MailRecord record = records.get(id);
            if (record != null) result.add(record);
        }
        result.sort((a, b) -> Long.compare(b.sentAt, a.sentAt));
        return result;
    }

    public int unreadCount(UUID recipient) { return unreadCounts.getOrDefault(recipient, 0); }
    public Collection<MailRecord> allRecords() { return Collections.unmodifiableCollection(records.values()); }

    public void setState(MailRecord record, int state) {
        if (record == null || record.state == state) return;
        if (record.state == MailRecord.PENDING && state != MailRecord.PENDING) removeFromDropBoxRef(record.id);
        if (record.state == MailRecord.BOXED && state != MailRecord.BOXED) removeFromLetterBoxRef(record.id);
        removeFromIndexes(record);
        record.state = state;
        addToIndexes(record);
        setDirty();
    }

    // ---- Letter Boxes ----

    public void registerLetterBox(UUID owner, PostalAddress address) {
        if (owner == null || address == null) return;
        letterBoxes.put(owner, address);
        setDirty();
    }

    public PostalAddress letterBox(UUID owner) { return owner == null ? null : letterBoxes.get(owner); }
    public boolean hasLetterBox(UUID owner) { return letterBox(owner) != null; }

    public UUID letterBoxOwnerAt(PostalAddress address) {
        if (address == null) return null;
        for (Map.Entry<UUID, PostalAddress> entry : letterBoxes.entrySet()) {
            if (address.equals(entry.getValue())) return entry.getKey();
        }
        return null;
    }

    public boolean isActiveLetterBox(UUID owner, PostalAddress address) {
        PostalAddress current = letterBoxes.get(owner);
        return current != null && current.equals(address);
    }

    /** Unregisters a broken box and returns its previously boxed mail to PENDING state. */
    public List<String> unregisterLetterBoxAt(PostalAddress address) {
        UUID owner = letterBoxOwnerAt(address);
        if (owner == null) return List.of();
        letterBoxes.remove(owner);
        List<String> ids = new ArrayList<>(letterBoxContents.getOrDefault(owner, List.of()));
        letterBoxContents.remove(owner);
        for (String id : ids) boxedOwnerByMail.remove(id);
        for (String id : ids) {
            MailRecord record = records.get(id);
            if (record != null && record.state == MailRecord.BOXED) {
                removeFromIndexes(record);
                record.state = MailRecord.PENDING;
                addToIndexes(record);
            }
        }
        setDirty();
        return ids;
    }

    public int letterBoxCount(UUID owner) { return letterBoxContents.getOrDefault(owner, List.of()).size(); }
    public boolean letterBoxHasSpace(UUID owner, int capacity) { return letterBoxCount(owner) < Math.max(1, capacity); }

    public boolean depositToLetterBox(MailRecord record, int capacity) {
        if (record == null || record.state != MailRecord.PENDING || !hasLetterBox(record.recipientUuid)) return false;
        if (!letterBoxHasSpace(record.recipientUuid, capacity)) return false;
        setState(record, MailRecord.BOXED);
        List<String> contents = letterBoxContents.computeIfAbsent(record.recipientUuid, ignored -> new ArrayList<>());
        if (!contents.contains(record.id)) contents.add(record.id);
        boxedOwnerByMail.put(record.id, record.recipientUuid);
        setDirty();
        return true;
    }

    public List<MailRecord> boxedFor(UUID owner) {
        List<String> ids = letterBoxContents.get(owner);
        if (ids == null || ids.isEmpty()) return List.of();
        List<MailRecord> result = new ArrayList<>();
        for (String id : new ArrayList<>(ids)) {
            MailRecord record = records.get(id);
            if (record != null && record.state == MailRecord.BOXED && owner.equals(record.recipientUuid)) result.add(record);
        }
        result.sort((a, b) -> Long.compare(a.sentAt, b.sentAt));
        return result;
    }

    public void markLetterBoxCollected(MailRecord record) {
        if (record != null && record.state == MailRecord.BOXED) setState(record, MailRecord.DELIVERED);
    }

    // ---- Drop Boxes ----

    public void registerDropBox(PostalAddress address) {
        if (address == null) return;
        dropBoxes.put(address.key(), address);
        setDirty();
    }

    public List<String> unregisterDropBox(PostalAddress address) {
        if (address == null) return List.of();
        String key = address.key();
        dropBoxes.remove(key);
        List<String> ids = new ArrayList<>(dropBoxQueues.getOrDefault(key, List.of()));
        dropBoxQueues.remove(key);
        for (String id : ids) dropBoxByMail.remove(id);
        setDirty();
        return ids;
    }

    public boolean postAtDropBox(PostalAddress address, String mailId, int capacity) {
        if (address == null || mailId == null || mailId.isBlank()) return false;
        MailRecord record = records.get(mailId);
        if (record == null || record.state != MailRecord.PENDING) return false;
        registerDropBox(address);
        List<String> queue = dropBoxQueues.computeIfAbsent(address.key(), ignored -> new ArrayList<>());
        if (queue.size() >= Math.max(1, capacity)) return false;
        if (!queue.contains(mailId)) queue.add(mailId);
        dropBoxByMail.put(mailId, address);
        setDirty();
        return true;
    }

    public PostalAddress dropBoxForMail(String mailId) { return mailId == null ? null : dropBoxByMail.get(mailId); }

    public void pickupFromDropBox(String mailId) {
        removeFromDropBoxRef(mailId);
        setDirty();
    }

    public int dropBoxQueueSize(PostalAddress address) {
        return address == null ? 0 : dropBoxQueues.getOrDefault(address.key(), List.of()).size();
    }

    // ---- Courier/Discord state ----

    public UUID courierUuid() { return courierUuid; }
    public boolean hasCourier() { return courierUuid != null; }

    public void bindCourier(UUID uuid, double x, double y, double z) {
        courierUuid = uuid;
        courierHomeSet = uuid != null;
        courierHomeX = x; courierHomeY = y; courierHomeZ = z;
        setDirty();
    }

    public void setCourierHome(double x, double y, double z) {
        if (courierUuid == null) return;
        courierHomeSet = true;
        courierHomeX = x; courierHomeY = y; courierHomeZ = z;
        setDirty();
    }

    public CourierHome courierHome() {
        return courierHomeSet ? new CourierHome(courierHomeX, courierHomeY, courierHomeZ) : null;
    }

    public void clearCourier() {
        courierUuid = null;
        courierHomeSet = false;
        setDirty();
    }

    public String discordPanelMessageId() { return discordPanelMessageId == null ? "" : discordPanelMessageId; }

    public void setDiscordPanelMessageId(String messageId) {
        String next = messageId == null ? "" : messageId.trim();
        if (!next.equals(discordPanelMessageId)) {
            discordPanelMessageId = next;
            setDirty();
        }
    }

    private void rebuildIndexes() {
        recipientIndex.clear();
        pendingIndex.clear();
        unreadCounts.clear();
        boxedOwnerByMail.clear();
        dropBoxByMail.clear();

        // Validate persisted Letter Box contents first.
        for (Iterator<Map.Entry<UUID, List<String>>> it = letterBoxContents.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, List<String>> entry = it.next();
            UUID owner = entry.getKey();
            entry.getValue().removeIf(id -> {
                MailRecord record = records.get(id);
                boolean valid = record != null && record.state == MailRecord.BOXED && owner.equals(record.recipientUuid);
                if (valid) boxedOwnerByMail.put(id, owner);
                return !valid;
            });
            if (entry.getValue().isEmpty()) it.remove();
        }

        // Any BOXED record without a surviving physical mailbox slot is safely returned to pending.
        for (MailRecord record : records.values()) {
            if (record.state == MailRecord.BOXED && !boxedOwnerByMail.containsKey(record.id)) record.state = MailRecord.PENDING;
        }

        // Validate Drop Box queues.
        for (Iterator<Map.Entry<String, List<String>>> it = dropBoxQueues.entrySet().iterator(); it.hasNext();) {
            Map.Entry<String, List<String>> entry = it.next();
            PostalAddress address = dropBoxes.get(entry.getKey());
            if (address == null) {
                it.remove();
                continue;
            }
            entry.getValue().removeIf(id -> {
                MailRecord record = records.get(id);
                boolean valid = record != null && record.state == MailRecord.PENDING;
                if (valid) dropBoxByMail.put(id, address);
                return !valid;
            });
            if (entry.getValue().isEmpty()) it.remove();
        }

        for (MailRecord record : records.values()) addToIndexes(record);
    }

    private void addToIndexes(MailRecord record) {
        recipientIndex.computeIfAbsent(record.recipientUuid, ignored -> new ArrayList<>()).add(record.id);
        if (record.state == MailRecord.PENDING) {
            pendingIndex.computeIfAbsent(record.recipientUuid, ignored -> new LinkedHashSet<>()).add(record.id);
        }
        if (record.state != MailRecord.READ) unreadCounts.merge(record.recipientUuid, 1, Integer::sum);
    }

    private void removeFromIndexes(MailRecord record) {
        List<String> recipientIds = recipientIndex.get(record.recipientUuid);
        if (recipientIds != null) {
            recipientIds.remove(record.id);
            if (recipientIds.isEmpty()) recipientIndex.remove(record.recipientUuid);
        }
        Set<String> pendingIds = pendingIndex.get(record.recipientUuid);
        if (pendingIds != null) {
            pendingIds.remove(record.id);
            if (pendingIds.isEmpty()) pendingIndex.remove(record.recipientUuid);
        }
        if (record.state != MailRecord.READ) {
            int count = unreadCounts.getOrDefault(record.recipientUuid, 0) - 1;
            if (count <= 0) unreadCounts.remove(record.recipientUuid);
            else unreadCounts.put(record.recipientUuid, count);
        }
    }

    private void removeFromDropBoxRef(String mailId) {
        PostalAddress address = dropBoxByMail.remove(mailId);
        if (address == null) return;
        List<String> queue = dropBoxQueues.get(address.key());
        if (queue != null) {
            queue.remove(mailId);
            if (queue.isEmpty()) dropBoxQueues.remove(address.key());
        }
    }

    private void removeFromLetterBoxRef(String mailId) {
        UUID owner = boxedOwnerByMail.remove(mailId);
        if (owner == null) return;
        List<String> contents = letterBoxContents.get(owner);
        if (contents != null) {
            contents.remove(mailId);
            if (contents.isEmpty()) letterBoxContents.remove(owner);
        }
    }

    private static List<String> parseIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (String part : csv.split(",")) {
            String id = part.trim();
            if (!id.isEmpty() && !result.contains(id)) result.add(id);
        }
        return result;
    }

    public record KnownPlayer(UUID uuid, String name) {}
    public record CourierHome(double x, double y, double z) {}

    public record PostalAddress(String dimension, int x, int y, int z) {
        public PostalAddress {
            if (dimension == null) dimension = "";
        }

        public String key() { return dimension + "|" + x + "|" + y + "|" + z; }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dimension", dimension);
            tag.putInt("X", x);
            tag.putInt("Y", y);
            tag.putInt("Z", z);
            return tag;
        }

        public static PostalAddress load(CompoundTag tag) {
            if (tag == null) return null;
            String dimension = tag.getString("Dimension");
            if (dimension == null || dimension.isBlank()) return null;
            return new PostalAddress(dimension, tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
        }
    }
}
