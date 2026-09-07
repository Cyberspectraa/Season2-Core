package uk.co.cyberspectra.spectralmail;

import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Small reflection boundary for dimension/block lookups so postal routing never force-loads chunks. */
public final class PostalRuntime {
    private PostalRuntime() {}

    public record ApproachPoint(double x, double y, double z) {}

    public static MailSavedData.PostalAddress address(Object level, BlockPos pos) {
        if (level == null || pos == null) return null;
        String dimension = dimensionId(level);
        if (dimension.isBlank()) return null;
        Integer x = intNoArg(pos, "getX", "m_123341_");
        Integer y = intNoArg(pos, "getY", "m_123342_");
        Integer z = intNoArg(pos, "getZ", "m_123343_");
        return x == null || y == null || z == null ? null : new MailSavedData.PostalAddress(dimension, x, y, z);
    }

    public static String dimensionId(Object levelOrEntity) {
        Object level = levelOrEntity;
        String className = levelOrEntity == null ? "" : levelOrEntity.getClass().getName();
        if (levelOrEntity != null && !className.endsWith("Level") && !className.endsWith("ServerLevel")) {
            Object resolved = MinecraftRuntime.levelOf(levelOrEntity);
            if (resolved != null) level = resolved;
        }
        Object key = invokeNoArg(level, "dimension", "m_46472_");
        Object location = invokeNoArg(key, "location", "m_135782_");
        return location == null ? "" : location.toString();
    }

    public static Object levelFor(MinecraftServer server, String dimension) {
        if (server == null || dimension == null || dimension.isBlank()) return null;
        for (Object level : MinecraftRuntime.serverLevels(server)) {
            if (dimension.equals(dimensionId(level))) return level;
        }
        return null;
    }

    public static Block blockAt(Object level, BlockPos pos) {
        Object state = blockStateAt(level, pos);
        Object block = invokeNoArg(state, "getBlock", "m_60734_");
        return block instanceof Block b ? b : null;
    }

    private static Object blockStateAt(Object level, BlockPos pos) {
        return invoke(level, new String[] {"getBlockState", "m_8055_"}, new Class<?>[] {BlockPos.class}, pos);
    }

    public static boolean loadedAndMatches(MinecraftServer server, MailSavedData.PostalAddress address, Block expected) {
        if (server == null || address == null || expected == null) return false;
        Object level = levelFor(server, address.dimension());
        if (level == null) return false;
        BlockPos pos = new BlockPos(address.x(), address.y(), address.z());
        Object loaded = invoke(level, new String[] {"hasChunkAt", "m_46805_"}, new Class<?>[] {BlockPos.class}, pos);
        if (!(loaded instanceof Boolean flag) || !flag) return false;
        return blockAt(level, pos) == expected;
    }

    public static double distanceSq(Object entity, MailSavedData.PostalAddress address) {
        if (entity == null || address == null) return Double.POSITIVE_INFINITY;
        double x = MinecraftRuntime.x(entity), y = MinecraftRuntime.y(entity), z = MinecraftRuntime.z(entity);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Double.POSITIVE_INFINITY;
        double dx = x - (address.x() + 0.5D);
        double dy = y - (address.y() + 0.5D);
        double dz = z - (address.z() + 0.5D);
        return dx * dx + dy * dy + dz * dz;
    }

    public static double distanceSq(Object entity, ApproachPoint point) {
        if (entity == null || point == null) return Double.POSITIVE_INFINITY;
        double x = MinecraftRuntime.x(entity), y = MinecraftRuntime.y(entity), z = MinecraftRuntime.z(entity);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Double.POSITIVE_INFINITY;
        double dx = x - point.x();
        double dy = y - point.y();
        double dz = z - point.z();
        return dx * dx + dy * dy + dz * dz;
    }

    public static boolean sameDimension(Object entity, MailSavedData.PostalAddress address) {
        return entity != null && address != null && address.dimension().equals(dimensionId(entity));
    }

    /** Legacy center target retained for compatibility with older call sites. */
    public static boolean moveTo(Object entity, MailSavedData.PostalAddress address, double speed) {
        return entity != null && address != null
                && MinecraftRuntime.moveToPosition(entity, address.x() + 0.5D, address.y(), address.z() + 0.5D, speed);
    }

    public static boolean moveTo(Object entity, ApproachPoint point, double speed) {
        return entity != null && point != null
                && MinecraftRuntime.moveToPosition(entity, point.x(), point.y(), point.z(), speed);
    }

    /**
     * Returns three service positions in priority order: directly in front of the postal block,
     * then the two front corners. The block's horizontal facing is read from the loaded blockstate,
     * so the courier approaches the visible mail slot/door rather than the block centre.
     */
    public static ApproachPoint[] servicePoints(MinecraftServer server, MailSavedData.PostalAddress address) {
        if (server == null || address == null) return new ApproachPoint[0];
        Object level = levelFor(server, address.dimension());
        if (level == null) return new ApproachPoint[0];
        BlockPos pos = new BlockPos(address.x(), address.y(), address.z());
        Object loaded = invoke(level, new String[] {"hasChunkAt", "m_46805_"}, new Class<?>[] {BlockPos.class}, pos);
        if (!(loaded instanceof Boolean flag) || !flag) return new ApproachPoint[0];

        Direction facing = Direction.NORTH;
        Object rawState = blockStateAt(level, pos);
        if (rawState instanceof BlockState state) {
            try {
                Object value = state.m_61143_(HorizontalDirectionalBlock.f_54117_);
                if (value instanceof Direction direction) facing = direction;
            } catch (RuntimeException ignored) {
            }
        }

        int fx = 0, fz = -1;
        int sx = 1, sz = 0;
        switch (facing.ordinal()) {
            case 3 -> { fx = 0; fz = 1; sx = -1; sz = 0; } // south
            case 4 -> { fx = -1; fz = 0; sx = 0; sz = -1; } // west
            case 5 -> { fx = 1; fz = 0; sx = 0; sz = 1; } // east
            default -> { fx = 0; fz = -1; sx = 1; sz = 0; } // north
        }

        double cx = address.x() + 0.5D;
        double cy = address.y();
        double cz = address.z() + 0.5D;
        double forward = 1.0D;
        double side = 0.85D;
        double px = cx + fx * forward;
        double pz = cz + fz * forward;

        return new ApproachPoint[] {
                new ApproachPoint(px, cy, pz),
                new ApproachPoint(px + sx * side, cy, pz + sz * side),
                new ApproachPoint(px - sx * side, cy, pz - sz * side)
        };
    }

    private static Integer intNoArg(Object target, String... names) {
        Object value = invokeNoArg(target, names);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Object invokeNoArg(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }

    private static Object invoke(Object target, String[] names, Class<?>[] types, Object... args) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name, types);
                return method.invoke(target, args);
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
