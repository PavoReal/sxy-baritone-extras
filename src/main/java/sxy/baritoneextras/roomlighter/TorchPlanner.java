package sxy.baritoneextras.roomlighter;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TorchPlanner {

    private TorchPlanner() {}

    public static List<BlockPos> plan(Level world, Set<BlockPos> floorBlocks, int threshold, BlockPos playerPos) {
        // Collect dark floor positions
        Set<BlockPos> darkSpots = new HashSet<>();
        for (BlockPos pos : floorBlocks) {
            if (world.getBrightness(LightLayer.BLOCK, pos) < threshold) {
                darkSpots.add(pos);
            }
        }

        if (darkSpots.isEmpty()) {
            return Collections.emptyList();
        }

        // Effective illumination radius (Manhattan distance from torch)
        int effectiveRadius = 14 - threshold;

        // Candidate positions are all floor blocks
        List<BlockPos> candidates = new ArrayList<>(floorBlocks);

        List<BlockPos> placements = new ArrayList<>();
        Set<BlockPos> remaining = new HashSet<>(darkSpots);

        // Greedy set-cover
        while (!remaining.isEmpty()) {
            BlockPos best = null;
            int bestCount = 0;

            for (BlockPos candidate : candidates) {
                int count = 0;
                for (BlockPos dark : remaining) {
                    if (manhattanDistance(candidate, dark) <= effectiveRadius) {
                        count++;
                    }
                }
                if (count > bestCount) {
                    bestCount = count;
                    best = candidate;
                }
            }

            if (best == null || bestCount == 0) {
                break;
            }

            placements.add(best);
            candidates.remove(best);

            // Remove covered dark spots
            BlockPos finalBest = best;
            remaining.removeIf(dark -> manhattanDistance(finalBest, dark) <= effectiveRadius);
        }

        // Sort by distance from player (nearest first)
        placements.sort(Comparator.comparingDouble(pos -> pos.distSqr(playerPos)));

        return placements;
    }

    private static int manhattanDistance(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX())
                + Math.abs(a.getY() - b.getY())
                + Math.abs(a.getZ() - b.getZ());
    }
}
