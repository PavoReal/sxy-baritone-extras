package sxy.baritoneextras.mineore;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public final class VeinScanner {

    private static final int MAX_VEIN_SIZE = 64;
    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
    };

    private VeinScanner() {}

    public static List<BlockPos> scanVein(Level level, BlockPos start, OreType oreType) {
        List<BlockPos> vein = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty() && vein.size() < MAX_VEIN_SIZE) {
            BlockPos pos = queue.poll();
            Block block = level.getBlockState(pos).getBlock();
            OreType type = OreType.classify(block);

            if (type != oreType) {
                continue;
            }

            vein.add(pos);

            for (int[] dir : DIRECTIONS) {
                BlockPos neighbor = pos.offset(dir[0], dir[1], dir[2]);
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        // Sort nearest-first from start position
        vein.sort(Comparator.comparingDouble(pos -> pos.distSqr(start)));
        return vein;
    }
}
