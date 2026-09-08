package com.season2.townlife.data;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class TownLifeSavedData extends SavedData {
    private static final String DATA_NAME = "townlife";
    private static final int DATA_VERSION = 3;

    private final Map<String, Town> towns = new LinkedHashMap<>();
    private final Map<UUID, Resident> residents = new LinkedHashMap<>();

    public TownLifeSavedData() {}

    public static TownLifeSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TownLifeSavedData::load, TownLifeSavedData::new, DATA_NAME);
    }

    public Collection<Town> towns() { return List.copyOf(towns.values()); }
    public Collection<Resident> residents() { return List.copyOf(residents.values()); }

    public Optional<Town> town(String id) {
        return Optional.ofNullable(towns.get(normalize(id)));
    }

    public boolean addTown(Town town) {
        if (towns.containsKey(town.id())) return false;
        towns.put(town.id(), town);
        setDirty();
        return true;
    }

    public boolean removeTown(String id) {
        String normalized = normalize(id);
        if (towns.remove(normalized) == null) return false;
        residents.values().removeIf(resident -> resident.townId().equals(normalized));
        setDirty();
        return true;
    }

    public Optional<Resident> resident(UUID uuid) {
        return Optional.ofNullable(residents.get(uuid));
    }

    public boolean addResident(Resident resident) {
        if (residents.containsKey(resident.entityUuid())) return false;
        residents.put(resident.entityUuid(), resident);
        setDirty();
        return true;
    }

    public boolean removeResident(UUID uuid) {
        if (residents.remove(uuid) == null) return false;
        setDirty();
        return true;
    }

    public long residentsInTown(String townId) {
        String normalized = normalize(townId);
        return residents.values().stream().filter(resident -> resident.townId().equals(normalized)).count();
    }

    public long assignmentsToLocation(String townId, String locationId, boolean home) {
        String town = normalize(townId);
        String location = normalize(locationId);
        return residents.values().stream()
                .filter(resident -> resident.townId().equals(town))
                .filter(resident -> home
                        ? resident.homeLocationId().equals(location)
                        : resident.workplaceLocationId().equals(location))
                .count();
    }

    public long currentOccupancy(String townId, String locationId) {
        String town = normalize(townId);
        String location = normalize(locationId);
        return residents.values().stream()
                .filter(resident -> resident.townId().equals(town))
                .filter(resident -> resident.targetLocationId().equals(location))
                .count();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        ListTag townList = new ListTag();
        for (Town town : towns.values()) townList.add(town.save());
        tag.put("Towns", townList);

        ListTag residentList = new ListTag();
        for (Resident resident : residents.values()) residentList.add(resident.save());
        tag.put("Residents", residentList);
        return tag;
    }

    public static TownLifeSavedData load(CompoundTag tag) {
        TownLifeSavedData data = new TownLifeSavedData();
        ListTag townList = tag.getList("Towns", Tag.TAG_COMPOUND);
        for (int i = 0; i < townList.size(); i++) {
            Town town = Town.load(townList.getCompound(i));
            data.towns.put(town.id(), town);
        }

        ListTag residentList = tag.getList("Residents", Tag.TAG_COMPOUND);
        for (int i = 0; i < residentList.size(); i++) {
            Resident resident = Resident.load(residentList.getCompound(i));
            data.residents.put(resident.entityUuid(), resident);
        }
        return data;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
