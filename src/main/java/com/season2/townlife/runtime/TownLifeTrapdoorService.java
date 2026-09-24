package com.season2.townlife.runtime;

import com.season2.townlife.data.Resident;
import com.season2.townlife.data.Town;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.data.TownLocation;
import com.season2.townlife.logic.Activity;
import com.season2.townlife.logic.TrapdoorPolicy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Opens only an unpowered wooden trapdoor directly ahead of a commuting resident,
 * over solid ground. No chunk loads, new navigation goals or floor-hole shortcuts.
 */
public final class TownLifeTrapdoorService {
    private static final Map<String, Map<BlockPos, Long>> OPENED = new HashMap<>();

    private TownLifeTrapdoorService() {}

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 10L != 0L) return;
        String dimension = level.dimension().location().toString();
        Map<BlockPos, Long> doors = OPENED.computeIfAbsent(dimension, ignored -> new HashMap<>());
        closePassedDoors(level, doors, now);

        TownLifeSavedData data = TownLifeSavedData.get(level);
        for (Resident resident : data.residents()) {
            if (resident.performing() || resident.activity() == Activity.IDLE || resident.activity() == Activity.SHELTER
                    || !resident.activity().equals(Activity.WORK) && resident.targetLocationId().isBlank()) continue;
            if (!"commuting".equals(TownLifeManager.modeName(resident.entityUuid()))
                    && !"errand".equals(TownLifeManager.modeName(resident.entityUuid()))) continue;
            if (!(level.getEntity(resident.entityUuid()) instanceof Mob mob) || mob.isSleeping()
                    || !EasyNpcCompat.isEasyNpc(mob) || Season2NpcProtection.isProtected(level, mob)) continue;

            Town town = data.town(resident.townId()).orElse(null);
            TownLocation destination = town == null ? null : town.location(resident.targetLocationId()).orElse(null);
            if (destination == null) continue;
            BlockPos goal = resident.activity() == Activity.WORK && resident.workPosition() != null
                    ? resident.workPosition() : destination.anchor();
            BlockPos origin = mob.blockPosition();
            BlockPos next = mob.getNavigation().getPath() != null && !mob.getNavigation().isDone()
                    ? mob.getNavigation().getPath().getNextNodePos() : goal;
            if (next.getX() == origin.getX() && next.getZ() == origin.getZ()) next = goal;
            int dx = next.getX() - origin.getX();
            int dz = next.getZ() - origin.getZ();
            if (dx == 0 && dz == 0) continue;
            Direction direction = Math.abs(dx) >= Math.abs(dz)
                    ? (dx > 0 ? Direction.EAST : Direction.WEST)
                    : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
            BlockPos feet = origin.relative(direction);
            // Only the adjacent block along the existing route is considered.
            tryOpen(level, doors, feet, feet, now);
            tryOpen(level, doors, feet.above(), feet, now);
        }
    }

    private static void tryOpen(ServerLevel level, Map<BlockPos, Long> doors,
                                BlockPos trapdoor, BlockPos feet, long now) {
        boolean loaded = level.hasChunkAt(trapdoor) && level.hasChunkAt(feet.below());
        if (!loaded || doors.containsKey(trapdoor)) return;
        BlockState state = level.getBlockState(trapdoor);
        if (!(state.getBlock() instanceof TrapDoorBlock) || !state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.getValue(TrapDoorBlock.OPEN) || !state.getFluidState().isEmpty()) return;
        boolean solidFloor = level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
        if (!TrapdoorPolicy.mayOpen(true, solidFloor, state.getValue(TrapDoorBlock.POWERED),
                loaded, true)) return;
        // Opening the flap never removes the supporting floor. Minecraft's
        // navigator remains responsible for deciding whether the gap is passable.
        if (level.setBlock(trapdoor, state.setValue(TrapDoorBlock.OPEN, true), 3)) {
            doors.put(trapdoor.immutable(), now + 60L);
        }
    }

    private static void closePassedDoors(ServerLevel level, Map<BlockPos, Long> doors, long now) {
        Iterator<Map.Entry<BlockPos, Long>> iterator = doors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (!level.hasChunkAt(pos)) {
                iterator.remove(); // Never load a chunk just to close a flap.
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof TrapDoorBlock) || !state.getValue(TrapDoorBlock.OPEN)
                    || state.getValue(TrapDoorBlock.POWERED)) {
                iterator.remove(); // A player or redstone has taken control.
                continue;
            }
            if (now < entry.getValue()
                    || !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(1.5D)).isEmpty()) continue;
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, false), 3);
            iterator.remove();
        }
    }
}
