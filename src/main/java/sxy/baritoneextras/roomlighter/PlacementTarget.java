package sxy.baritoneextras.roomlighter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class PlacementTarget {
    public final BlockPos against;
    public final Direction face;
    public final Vec3 hitVec;

    public PlacementTarget(BlockPos against, Direction face, Vec3 hitVec) {
        this.against = against;
        this.face = face;
        this.hitVec = hitVec;
    }
}
