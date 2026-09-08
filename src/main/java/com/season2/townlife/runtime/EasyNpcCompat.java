package com.season2.townlife.runtime;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.common.ForgeMod;

/**
 * Compatibility boundary for Easy NPC 7.11+.
 *
 * <p>Town Life never imports a full Easy NPC preset over a resident because
 * that would overwrite the player's skin, name, equipment, dialogs or trades.
 * Instead this class applies only the movement/objective pieces of the Town
 * Resident preset through Easy NPC's public runtime API.</p>
 */
public final class EasyNpcCompat {
    private static final String EASY_NPC_INTERFACE = "de.markusbordihn.easynpc.entity.easynpc.EasyNPC";
    private static final String OBJECTIVE_TYPE = "de.markusbordihn.easynpc.data.objective.ObjectiveType";
    private static final String OBJECTIVE_ENTRY = "de.markusbordihn.easynpc.data.objective.ObjectiveDataEntry";
    private static final String NAVIGATION_TYPE = "de.markusbordihn.easynpc.data.attribute.NavigationType";

    private static final Set<String> BASE_RESIDENT_OBJECTIVES = Set.of(
            "LOOK_AT_PLAYER", "LOOK_AT_MOB", "LOOK_AT_RESET",
            "OPEN_DOOR", "CLOSE_DOOR", "FLOAT");

    private static volatile boolean interfaceLookupDone;
    private static Class<?> easyNpcClass;

    private EasyNpcCompat() {}

    public static boolean isEasyNpc(Entity entity) {
        if (entity == null) return false;
        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeId != null && "easy_npc".equals(typeId.getNamespace())) return true;
        Class<?> apiClass = getEasyNpcClass();
        return apiClass != null && apiClass.isInstance(entity);
    }

    public static Optional<Mob> asControllableMob(Entity entity) {
        if (!isEasyNpc(entity) || !(entity instanceof Mob mob)) return Optional.empty();
        return Optional.of(mob);
    }

    /**
     * Applies the Town Resident movement preset without replacing cosmetic,
     * dialog, trade or equipment data.
     */
    public static boolean applyTownResidentPreset(Mob mob) {
        if (!isEasyNpc(mob)) return false;
        boolean changed = false;
        changed |= configureMovementAttributes(mob);
        changed |= configureInteractionAttributes(mob);
        changed |= configureStepHeight(mob);
        changed |= configureBaseObjectives(mob);
        prepareNavigation(mob);
        return changed;
    }

    /** Commute/errand state: the active Easy NPC home becomes the destination. */
    public static boolean enterTravelState(Mob mob, BlockPos destination, double speed) {
        if (!isEasyNpc(mob) || destination == null) return false;
        mob.getNavigation().stop();
        boolean ok = setHomePosition(mob, destination);
        removeObjective(mob, "RANDOM_STROLL_AROUND_HOME");
        ok |= ensureObjective(mob, "MOVE_BACK_TO_HOME", speed, 1.6F);
        prepareNavigation(mob);
        return ok;
    }

    /** Home/work state: stay around the current anchor and stroll naturally. */
    public static boolean enterLocalState(Mob mob, BlockPos anchor, double speed) {
        if (!isEasyNpc(mob) || anchor == null) return false;
        mob.getNavigation().stop();
        boolean ok = setHomePosition(mob, anchor);
        ok |= ensureObjective(mob, "MOVE_BACK_TO_HOME", speed, 2.0F);
        ok |= ensureObjective(mob, "RANDOM_STROLL_AROUND_HOME", speed, 2.0F);
        prepareNavigation(mob);
        return ok;
    }

    /** Sleeping/conversation state: remove movement goals that could fight Town Life. */
    public static void enterStationaryState(Mob mob) {
        if (!isEasyNpc(mob)) return;
        removeObjective(mob, "MOVE_BACK_TO_HOME");
        removeObjective(mob, "RANDOM_STROLL_AROUND_HOME");
        mob.getNavigation().stop();
    }

    /**
     * Ask Easy NPC to refresh configured navigation flags before Town Life
     * starts a route. Open/Close/Pass Door behavior remains owned by Easy NPC.
     */
    public static void prepareNavigation(Mob mob) {
        if (!isEasyNpc(mob)) return;
        invokeNoThrow(mob, "refreshNavigation");
        invokeNoThrow(mob, "registerAttributeBasedObjectives");
    }

    /**
     * Wide-range Minecraft path fallback. Easy NPC's Move Back To Home goal
     * deliberately searches only 48 blocks; Town Life uses a larger single
     * path when a normal town commute is longer than that. No stair/door hops
     * or geometric waypoints are generated.
     */
    public static boolean startWidePath(Mob mob, BlockPos target, double speed, int range) {
        if (mob == null || target == null) return false;
        prepareNavigation(mob);
        try {
            var path = mob.getNavigation().createPath(target, 1, Math.max(48, range));
            return path != null && mob.getNavigation().moveTo(path, speed);
        } catch (RuntimeException ignored) {
            return mob.getNavigation().moveTo(
                    target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, speed);
        }
    }

    public static boolean startSleeping(Mob mob, BlockPos bedPos) {
        mob.getNavigation().stop();
        mob.startSleeping(bedPos);
        return mob.isSleeping() && mob.getSleepingPos().filter(bedPos::equals).isPresent();
    }

    public static void stopSleeping(Mob mob) {
        if (mob.isSleeping() || mob.getSleepingPos().isPresent()) {
            mob.stopSleeping();
            return;
        }
        // Defensive cleanup only. We never use Pose.SLEEPING as a start fallback.
        if (mob.getPose() == Pose.SLEEPING) mob.setPose(Pose.STANDING);
    }

    private static boolean configureMovementAttributes(Mob mob) {
        try {
            Object attributeData = invoke(mob, "getEasyNPCAttributeData");
            if (attributeData == null) return false;
            Object entityAttributes = invoke(attributeData, "getEntityAttributes");
            if (entityAttributes == null) return false;
            Object movement = invoke(entityAttributes, "getMovementAttributes");
            if (movement == null) return false;

            movement = invokeBooleanBuilder(movement, "withCanOpenDoor", true);
            movement = invokeBooleanBuilder(movement, "withCanCloseDoor", true);
            movement = invokeBooleanBuilder(movement, "withCanPassDoor", true);
            movement = invokeBooleanBuilder(movement, "withIsImmovable", false);

            // DEFAULT is Easy NPC's automatic navigation choice (ground for a
            // normal humanoid resident) and avoids hard-coding a renderer type.
            try {
                Class<?> navType = Class.forName(NAVIGATION_TYPE);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object automatic = Enum.valueOf((Class) navType, "DEFAULT");
                Method withNavigation = movement.getClass().getMethod("withNavigationType", navType);
                movement = withNavigation.invoke(movement, automatic);
            } catch (ReflectiveOperationException ignored) {}

            Method setter = findCompatibleMethod(entityAttributes.getClass(), "setMovementAttributes", movement.getClass());
            if (setter == null) return false;
            setter.invoke(entityAttributes, movement);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean configureStepHeight(Mob mob) {
        try {
            AttributeInstance stepHeight = mob.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
            if (stepHeight == null) return false;
            // Humanoids already step roughly half a block. A +0.5 addition
            // gives Town Residents about one-block tolerance for stairs/slabs
            // without changing their walking speed or making them jump.
            if (stepHeight.getBaseValue() < 0.5D) stepHeight.setBaseValue(0.5D);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean configureInteractionAttributes(Mob mob) {
        try {
            Object attributeData = invoke(mob, "getEasyNPCAttributeData");
            if (attributeData == null) return false;
            Object entityAttributes = invoke(attributeData, "getEntityAttributes");
            if (entityAttributes == null) return false;
            Object interaction = invoke(entityAttributes, "getInteractionAttributes");
            if (interaction == null) return false;
            interaction = invokeBooleanBuilder(interaction, "withIsPushable", true);
            interaction = invokeBooleanBuilder(interaction, "withPushEntities", true);
            Method setter = findCompatibleMethod(entityAttributes.getClass(), "setInteractionAttributes", interaction.getClass());
            if (setter == null) return false;
            setter.invoke(entityAttributes, interaction);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean configureBaseObjectives(Mob mob) {
        try {
            Object dataSet = invoke(mob, "getObjectiveDataSet");
            if (dataSet == null) return false;
            Object objectives = invoke(dataSet, "getObjectives");
            if (objectives instanceof Collection<?> collection) {
                for (Object entry : new ArrayList<>(collection)) {
                    Object type = invoke(entry, "getType");
                    String name = type instanceof Enum<?> e ? e.name() : String.valueOf(type);
                    if (!BASE_RESIDENT_OBJECTIVES.contains(name)) {
                        removeObjectiveEntry(mob, dataSet, entry);
                    }
                }
            }
            ensureObjective(mob, "LOOK_AT_PLAYER", 0.0D, 0F);
            ensureObjective(mob, "LOOK_AT_MOB", 0.0D, 0F);
            ensureObjective(mob, "LOOK_AT_RESET", 0.0D, 0F);
            invokeNoThrow(mob, "registerAttributeBasedObjectives");
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static void removeObjectiveEntry(Mob mob, Object dataSet, Object entry) {
        try {
            Method removeCustom = findCompatibleMethod(mob.getClass(), "removeCustomObjective", entry.getClass());
            if (removeCustom != null) removeCustom.invoke(mob, entry);
        } catch (ReflectiveOperationException ignored) {}
        try {
            Method removeData = findCompatibleMethod(dataSet.getClass(), "removeObjective", entry.getClass());
            if (removeData != null) removeData.invoke(dataSet, entry);
        } catch (ReflectiveOperationException ignored) {}
    }

    private static boolean ensureObjective(Mob mob, String typeName, double speed, float stopDistance) {
        try {
            Class<?> typeClass = Class.forName(OBJECTIVE_TYPE);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object type = Enum.valueOf((Class) typeClass, typeName);

            Object existing = null;
            try {
                Method getter = mob.getClass().getMethod("getObjective", typeClass);
                existing = getter.invoke(mob, type);
            } catch (ReflectiveOperationException ignored) {}

            Object entry = existing;
            if (entry == null) {
                Class<?> entryClass = Class.forName(OBJECTIVE_ENTRY);
                Constructor<?> ctor = entryClass.getConstructor(typeClass);
                entry = ctor.newInstance(type);
            }

            if (speed > 0D) {
                try { entry.getClass().getMethod("setSpeedModifier", double.class).invoke(entry, speed); }
                catch (ReflectiveOperationException ignored) {}
            }
            if (stopDistance > 0F && "MOVE_BACK_TO_HOME".equals(typeName)) {
                try { entry.getClass().getMethod("setStopDistance", float.class).invoke(entry, stopDistance); }
                catch (ReflectiveOperationException ignored) {}
            }

            Method add = findCompatibleMethod(mob.getClass(), "addOrUpdateCustomObjective", entry.getClass());
            if (add != null) {
                add.invoke(mob, entry);
                return true;
            }
            Method basicAdd = findCompatibleMethod(mob.getClass(), "addObjective", entry.getClass());
            if (basicAdd != null) {
                basicAdd.invoke(mob, entry);
                invokeNoThrow(mob, "refreshCustomObjectives");
                return true;
            }
        } catch (ReflectiveOperationException ignored) {}
        return false;
    }

    private static void removeObjective(Mob mob, String typeName) {
        try {
            Class<?> typeClass = Class.forName(OBJECTIVE_TYPE);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object type = Enum.valueOf((Class) typeClass, typeName);
            Method remove = mob.getClass().getMethod("removeCustomObjective", typeClass);
            remove.invoke(mob, type);
        } catch (ReflectiveOperationException ignored) {
            // Fall back to removing only the stored objective if this Easy NPC
            // build does not expose removeCustomObjective(ObjectiveType).
            try {
                Object dataSet = invoke(mob, "getObjectiveDataSet");
                if (dataSet == null) return;
                Class<?> typeClass = Class.forName(OBJECTIVE_TYPE);
                @SuppressWarnings({"unchecked", "rawtypes"})
                Object type = Enum.valueOf((Class) typeClass, typeName);
                dataSet.getClass().getMethod("removeObjective", typeClass).invoke(dataSet, type);
            } catch (ReflectiveOperationException ignoredAgain) {}
        }
    }

    private static boolean setHomePosition(Mob mob, BlockPos position) {
        try {
            Method method = mob.getClass().getMethod("setHomePosition", BlockPos.class);
            method.invoke(mob, position.immutable());
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Object invokeBooleanBuilder(Object target, String methodName, boolean value)
            throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName, boolean.class).invoke(target, value);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private static void invokeNoThrow(Object target, String methodName) {
        try { target.getClass().getMethod(methodName).invoke(target); }
        catch (ReflectiveOperationException ignored) {}
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> argumentType) {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            if (method.getParameterTypes()[0].isAssignableFrom(argumentType)) return method;
        }
        return null;
    }

    private static Class<?> getEasyNpcClass() {
        if (!interfaceLookupDone) {
            synchronized (EasyNpcCompat.class) {
                if (!interfaceLookupDone) {
                    try { easyNpcClass = Class.forName(EASY_NPC_INTERFACE); }
                    catch (ClassNotFoundException ignored) { easyNpcClass = null; }
                    interfaceLookupDone = true;
                }
            }
        }
        return easyNpcClass;
    }
}
