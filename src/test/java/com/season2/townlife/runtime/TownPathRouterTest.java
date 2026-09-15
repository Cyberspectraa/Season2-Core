package com.season2.townlife.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.season2.townlife.data.TownPathType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class TownPathRouterTest {
    @Test
    void equalLengthRoutesPreferMainRoadOverNormalPath() {
        Map<Long, TownPathType> paths = new LinkedHashMap<>();
        add(paths, new BlockPos(0, 64, 0), TownPathType.NORMAL);
        add(paths, new BlockPos(4, 64, 0), TownPathType.NORMAL);

        for (int x = 0; x <= 4; x++) {
            add(paths, new BlockPos(x, 64, 1), TownPathType.MAIN);
            add(paths, new BlockPos(x, 64, -1), TownPathType.NORMAL);
        }

        List<BlockPos> route = TownPathRouter.route(
                paths,
                new BlockPos(0, 65, 0),
                new BlockPos(4, 65, 0),
                3,
                2,
                100);

        assertFalse(route.isEmpty());
        assertTrue(route.stream().anyMatch(pos -> pos.getZ() == 1), "route should use the Main Road branch");
        assertFalse(route.stream().anyMatch(pos -> pos.getZ() == -1), "route should avoid the equal Normal Path branch");
    }

    private static void add(Map<Long, TownPathType> paths, BlockPos pos, TownPathType type) {
        paths.put(pos.asLong(), type);
    }
}
