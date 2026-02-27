package sxy.baritoneextras.roomlighter;

import net.minecraft.core.BlockPos;

import java.util.Set;

public class RoomScanResult {
    public final Set<BlockPos> airBlocks;
    public final Set<BlockPos> floorBlocks;
    public final boolean cappedByRadius;
    public final boolean cappedByVolume;

    public RoomScanResult(Set<BlockPos> airBlocks, Set<BlockPos> floorBlocks,
                          boolean cappedByRadius, boolean cappedByVolume) {
        this.airBlocks = airBlocks;
        this.floorBlocks = floorBlocks;
        this.cappedByRadius = cappedByRadius;
        this.cappedByVolume = cappedByVolume;
    }
}
