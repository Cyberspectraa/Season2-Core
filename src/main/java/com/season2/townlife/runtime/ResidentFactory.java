package com.season2.townlife.runtime;

import com.season2.townlife.data.Resident;
import com.season2.townlife.data.Town;
import com.season2.townlife.logic.DailyFocus;
import com.season2.townlife.logic.PersonalityTrait;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

public final class ResidentFactory {
    private static final List<String> NAMES = List.of(
            "Alden", "Anya", "Bram", "Cora", "Della", "Ellis", "Elara", "Finn",
            "Galen", "Hollis", "Iris", "Jory", "Kael", "Lena", "Lyra", "Mara",
            "Nell", "Orin", "Perrin", "Quinn", "Rhea", "Rowan", "Soren", "Talia",
            "Tomas", "Una", "Vera", "Wren", "Yara", "Zane");

    private ResidentFactory() {}

    public static Resident create(Mob mob, Town town) {
        long seed = mob.getUUID().getMostSignificantBits() ^ mob.getUUID().getLeastSignificantBits();
        Random random = new Random(seed);
        String identityName = mob.hasCustomName() ? mob.getCustomName().getString() : randomName(random, mob.getUUID());
        if (!mob.hasCustomName()) {
            mob.setCustomName(Component.literal(identityName));
            mob.setCustomNameVisible(true);
        }

        Resident resident = new Resident(mob.getUUID(), town.id(), identityName);
        randomizePersonality(resident, random);
        return resident;
    }

    private static void randomizePersonality(Resident resident, Random random) {
        PersonalityTrait[] values = PersonalityTrait.values();
        PersonalityTrait first = values[random.nextInt(values.length)];
        PersonalityTrait second = values[random.nextInt(values.length)];
        int guard = 0;
        while (second == first && guard++ < 12) second = values[random.nextInt(values.length)];
        resident.setTraits(EnumSet.of(first, second));

        int wake = 400 + random.nextInt(900) - 450;
        int sleep = 12800 + random.nextInt(1400) - 700;
        if (resident.traits().contains(PersonalityTrait.EARLY_BIRD)) {
            wake -= 700;
            sleep -= 400;
        }
        if (resident.traits().contains(PersonalityTrait.NIGHT_OWL)) {
            wake += 900;
            sleep += 1000;
        }
        int workStart = 1600 + random.nextInt(800);
        int workEnd = 9200 + random.nextInt(1100);
        resident.setSchedule(wake, sleep, workStart, workEnd);
        resident.setDailyFocus(DailyFocus.BALANCED);
    }

    private static String randomName(Random random, UUID uuid) {
        String base = NAMES.get(random.nextInt(NAMES.size()));
        int suffix = Math.floorMod(uuid.hashCode(), 97);
        return suffix < 10 ? base : base + " " + (suffix + 1);
    }
}
