package sxy.baritoneextras.roomlighter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

public final class RoomScanner {

    private RoomScanner() {}

    public static RoomScanResult scan(Level world, BlockPos start, int maxRadius, int maxVolume) {
        Set<BlockPos> airBlocks = new HashSet<>();
        Set<BlockPos> floorBlocks = new HashSet<>();
        boolean cappedByRadius = false;
        boolean cappedByVolume = false;

        Queue<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            if (visited.size() >= maxVolume) {
                cappedByVolume = true;
                break;
            }

            BlockPos pos = queue.poll();
            BlockState state = world.getBlockState(pos);

            if (state.isCollisionShapeFullBlock(world, pos)) {
                continue;
            }

            airBlocks.add(pos);

            // Check if this is a floor block (air with sturdy solid below)
            BlockPos below = pos.below();
            BlockState belowState = world.getBlockState(below);
            if (belowState.isFaceSturdy(world, below, Direction.UP)) {
                floorBlocks.add(pos);
            }

            // Expand to 6 neighbors
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                if (visited.contains(neighbor)) {
                    continue;
                }

                int dx = Math.abs(neighbor.getX() - start.getX());
                int dy = Math.abs(neighbor.getY() - start.getY());
                int dz = Math.abs(neighbor.getZ() - start.getZ());
                int chebyshev = Math.max(dx, Math.max(dy, dz));

                if (chebyshev > maxRadius) {
                    cappedByRadius = true;
                    continue;
                }

                visited.add(neighbor);
                queue.add(neighbor);
            }
        }

        return new RoomScanResult(
                Collections.unmodifiableSet(airBlocks),
                Collections.unmodifiableSet(floorBlocks),
                cappedByRadius,
                cappedByVolume
        );
    }
}
