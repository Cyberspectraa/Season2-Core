package uk.co.cyberspectra.spectralmail;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * Reflection boundary for the courier. EasyNPC is intentionally optional at compile time: the
 * courier drives any loaded EasyNPC mob through its normal vanilla navigation object without
 * linking client classes or forcing an EasyNPC API dependency.
 */
public final class MinecraftRuntime {
    private static final ConcurrentMap<Class<?>, Method> LEVEL_METHOD = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> UUID_METHOD = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> X_METHOD = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> Y_METHOD = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> Z_METHOD = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> NAV_METHOD = new ConcurrentHashMap<>();

    private MinecraftRuntime() {}

    public static Object findNearestEasyNpc(ServerPlayer player, double maxDistance) {
        Object level = levelOf(player);
        if (level == null) return null;
        Object nearest = null;
        double best = maxDistance * maxDistance;
        for (Object entity : loadedEntities(level)) {
            if (!isEasyNpc(entity)) continue;
            double distance = distanceSq(player, entity);
            if (distance <= best) {
                best = distance;
                nearest = entity;
            }
        }
        return nearest;
    }

    public static boolean isEasyNpc(Object entity) {
        if (entity == null) return false;
        String name = entity.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("easynpc") || name.contains("easy_npc");
    }

    public static Object findLoadedEntity(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        for (Object level : serverLevels(server)) {
            for (Object entity : loadedEntities(level)) {
                UUID found = uuidOf(entity);
                if (uuid.equals(found)) return entity;
            }
        }
        return null;
    }

    public static ServerPlayer findOnlinePlayer(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return null;
        Object playerList = invokeNoArg(server, "getPlayerList", "m_6846_");
        if (playerList == null) return null;

        for (Method method : playerList.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 1 && params[0] == UUID.class
                    && ServerPlayer.class.isAssignableFrom(method.getReturnType())) {
                try {
                    return (ServerPlayer) method.invoke(playerList, uuid);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return null;
    }

    public static boolean isOperator(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return false;
        Object playerList = invokeNoArg(server, "getPlayerList", "m_6846_");
        if (playerList == null) return false;

        Object profile = null;
        for (Method candidate : player.getClass().getMethods()) {
            if (candidate.getParameterCount() == 0
                    && candidate.getReturnType().getName().equals("com.mojang.authlib.GameProfile")) {
                try {
                    profile = candidate.invoke(player);
                    if (profile != null) break;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        if (profile == null) return false;

        for (String name : new String[] {"isOp", "m_11303_"}) {
            try {
                Method method = playerList.getClass().getMethod(name, profile.getClass());
                Object result = method.invoke(playerList, profile);
                if (result instanceof Boolean flag) return flag;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    public static List<ServerPlayer> onlinePlayers(MinecraftServer server) {
        if (server == null) return Collections.emptyList();
        Object playerList = invokeNoArg(server, "getPlayerList", "m_6846_");
        if (playerList == null) return Collections.emptyList();
        Object result = invokeNoArg(playerList, "getPlayers", "m_11314_");
        if (result instanceof Iterable<?> iterable) {
            List<ServerPlayer> players = new ArrayList<>();
            for (Object object : iterable) if (object instanceof ServerPlayer p) players.add(p);
            return players;
        }
        return Collections.emptyList();
    }

    public static Object levelOf(Object entity) {
        if (entity == null) return null;
        Class<?> type = entity.getClass();
        Method cached = LEVEL_METHOD.get(type);
        if (cached != null) return invoke(entity, cached);

        Method method = findNamedNoArg(type, "level", "m_9236_");
        if (method == null) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getParameterCount() == 0
                        && candidate.getReturnType().getName().endsWith(".Level")) {
                    method = candidate;
                    break;
                }
            }
        }
        if (method != null) {
            method.setAccessible(true);
            LEVEL_METHOD.put(type, method);
            return invoke(entity, method);
        }
        return null;
    }

    public static boolean sameLevel(Object a, Object b) {
        Object levelA = levelOf(a);
        Object levelB = levelOf(b);
        return levelA != null && levelA == levelB;
    }

    public static UUID uuidOf(Object entity) {
        if (entity == null) return null;
        Class<?> type = entity.getClass();
        Method cached = UUID_METHOD.get(type);
        if (cached != null) return (UUID) invoke(entity, cached);
        Method method = findNamedNoArg(type, "getUUID", "m_20148_");
        if (method != null && method.getReturnType() == UUID.class) {
            method.setAccessible(true);
            UUID_METHOD.put(type, method);
            return (UUID) invoke(entity, method);
        }
        return null;
    }

    public static double x(Object entity) { return coordinate(entity, 'x'); }
    public static double y(Object entity) { return coordinate(entity, 'y'); }
    public static double z(Object entity) { return coordinate(entity, 'z'); }

    public static double distanceSq(Object a, Object b) {
        double ax = x(a), ay = y(a), az = z(a);
        double bx = x(b), by = y(b), bz = z(b);
        if (!Double.isFinite(ax) || !Double.isFinite(ay) || !Double.isFinite(az)
                || !Double.isFinite(bx) || !Double.isFinite(by) || !Double.isFinite(bz)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean moveToEntity(Object mob, Object target, double speed) {
        Object navigation = navigation(mob);
        if (navigation == null || target == null) return false;
        for (Method method : navigation.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 2 && params[1] == double.class
                    && params[0].isAssignableFrom(target.getClass())
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                try {
                    Object result = method.invoke(navigation, target, speed);
                    return Boolean.TRUE.equals(result);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return false;
    }

    public static boolean moveToPosition(Object mob, double x, double y, double z, double speed) {
        Object navigation = navigation(mob);
        if (navigation == null) return false;
        for (Method method : navigation.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 4 && p[0] == double.class && p[1] == double.class
                    && p[2] == double.class && p[3] == double.class
                    && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
                try {
                    return Boolean.TRUE.equals(method.invoke(navigation, x, y, z, speed));
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return false;
    }

    public static void stopNavigation(Object mob) {
        Object navigation = navigation(mob);
        if (navigation == null) return;
        Method method = findNamedNoArg(navigation.getClass(), "stop", "m_26573_");
        if (method != null && method.getReturnType() == void.class) invoke(navigation, method);
    }

    public static void setMainHand(Object entity, ItemStack stack) {
        if (entity == null || stack == null) return;
        for (Method method : entity.getClass().getMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (p.length == 2 && p[0] == InteractionHand.class && p[1] == ItemStack.class) {
                try {
                    method.invoke(entity, InteractionHand.MAIN_HAND, stack);
                    return;
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
    }

    public static void clearMainHand(Object entity) {
        setMainHand(entity, ItemStack.f_41583_);
    }

    public static boolean teleport(Object entity, double x, double y, double z) {
        if (entity == null) return false;
        for (String name : new String[] {"teleportTo", "m_6021_", "setPos", "m_6034_"}) {
            try {
                Method method = entity.getClass().getMethod(name, double.class, double.class, double.class);
                method.invoke(entity, x, y, z);
                return true;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return false;
    }

    public static List<Object> serverLevels(MinecraftServer server) {
        if (server == null) return Collections.emptyList();
        Object levels = invokeNoArg(server, "getAllLevels", "m_129785_");
        if (!(levels instanceof Iterable<?>)) {
            levels = firstIterableResult(server, "ServerLevel");
        }
        if (!(levels instanceof Iterable<?> iterable)) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (Object level : iterable) {
            if (level instanceof ServerLevel || (level != null && level.getClass().getName().endsWith("ServerLevel"))) {
                result.add(level);
            }
        }
        return result;
    }

    public static List<Object> loadedEntities(Object level) {
        if (level == null) return Collections.emptyList();
        Object entities = invokeNoArg(level, "getAllEntities", "m_8872_");
        if (!(entities instanceof Iterable<?>)) {
            entities = firstIterableResult(level, ".entity.");
        }
        if (!(entities instanceof Iterable<?> iterable)) return Collections.emptyList();
        List<Object> result = new ArrayList<>();
        for (Object entity : iterable) if (entity != null) result.add(entity);
        return result;
    }

    private static Object navigation(Object mob) {
        if (mob == null) return null;
        Class<?> type = mob.getClass();
        Method cached = NAV_METHOD.get(type);
        if (cached != null) return invoke(mob, cached);
        Method method = findNamedNoArg(type, "getNavigation", "m_21573_");
        if (method == null) {
            for (Method candidate : type.getMethods()) {
                if (candidate.getParameterCount() == 0
                        && candidate.getReturnType().getName().contains("PathNavigation")) {
                    method = candidate;
                    break;
                }
            }
        }
        if (method != null) {
            method.setAccessible(true);
            NAV_METHOD.put(type, method);
            return invoke(mob, method);
        }
        return null;
    }

    private static double coordinate(Object entity, char axis) {
        if (entity == null) return Double.NaN;
        Class<?> type = entity.getClass();
        ConcurrentMap<Class<?>, Method> cache = axis == 'x' ? X_METHOD : axis == 'y' ? Y_METHOD : Z_METHOD;
        Method method = cache.get(type);
        if (method == null) {
            String named = axis == 'x' ? "getX" : axis == 'y' ? "getY" : "getZ";
            String srg = axis == 'x' ? "m_20185_" : axis == 'y' ? "m_20186_" : "m_20189_";
            method = findNamedNoArg(type, named, srg);
            if (method != null && (method.getReturnType() == double.class || method.getReturnType() == Double.class)) {
                method.setAccessible(true);
                cache.put(type, method);
            } else {
                method = null;
            }
        }
        Object value = method == null ? null : invoke(entity, method);
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    private static Method findNamedNoArg(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Object firstIterableResult(Object target, String expectedClassFragment) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || !Iterable.class.isAssignableFrom(method.getReturnType())) continue;
            try {
                Object result = method.invoke(target);
                if (!(result instanceof Iterable<?> iterable)) continue;
                for (Object sample : iterable) {
                    if (sample == null) continue;
                    if (sample.getClass().getName().contains(expectedClassFragment)) return result;
                    break;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        Method method = findNamedNoArg(target.getClass(), names);
        return method == null ? null : invoke(target, method);
    }

    private static Object invoke(Object target, Method method) {
        try {
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
