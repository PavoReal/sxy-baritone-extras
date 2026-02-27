package sxy.baritoneextras.torchplacer;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class TorchPlacerProcess implements IBaritoneProcess {

    private static final int OFFHAND_SLOT = -2;
    private static final int AIMING_TICKS = 5;
    private static final int AIMING_TIMEOUT = 15;
    private static final int PLACING_TICKS = 5;
    private static final int TORCH_LIGHT_RADIUS = 14;

    private enum State { IDLE, AIMING, PLACING }

    private final IBaritone baritone;
    private final IPlayerContext ctx;
    private final TorchPlacerConfig config;

    private State state = State.IDLE;
    private int ticksInState;
    private PlacementTarget currentTarget;
    private Rotation currentRot;
    private int currentTorchSlot;

    private boolean warned;
    private BlockPos lastTorchPos;
    private int lastTorchMovementIndex = -1;
    private IPathExecutor lastTorchPathExecutor = null;

    public TorchPlacerProcess(IBaritone baritone, TorchPlacerConfig config) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        this.config = config;
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        if (!config.enabled) {
            warned = false;
            resetState();
            return false;
        }
        if (state != State.IDLE) {
            return true;
        }
        if (!baritone.getPathingBehavior().hasPath()) {
            return false;
        }
        int lightLevel = ctx.world().getBrightness(LightLayer.BLOCK, ctx.playerFeet());
        return lightLevel < config.lightLevelThreshold;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (state != State.PLACING) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        }

        if (!isSafeToCancel) {
            if (state == State.IDLE) {
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        switch (state) {
            case IDLE:
                return tickIdle();
            case AIMING:
                return tickAiming();
            case PLACING:
                return tickPlacing();
            default:
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
        }
    }

    private PathingCommand tickIdle() {
        BlockPos feet = ctx.playerFeet();

        if (!shouldPlaceNow(feet)) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        int torchSlot = findTorchSlot();
        if (torchSlot == -1) {
            if (!warned) {
                Helper.HELPER.logDirect("No torches available for automatic placement");
                warned = true;
            }
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        PlacementTarget target = findPlacementTarget(feet);
        if (target == null) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, target.against, target.hitVec, blockReachDistance, false);
        if (!rot.isPresent()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        currentTarget = target;
        currentRot = rot.get();
        currentTorchSlot = torchSlot;
        state = State.AIMING;
        ticksInState = 0;

        baritone.getLookBehavior().updateTarget(currentRot, true);
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickAiming() {
        if (!isTargetValid(currentTarget)) {
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, currentTarget.against, currentTarget.hitVec, blockReachDistance, false);
        if (!rot.isPresent()) {
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        currentRot = rot.get();

        baritone.getLookBehavior().updateTarget(currentRot, true);
        ticksInState++;

        if (ticksInState >= AIMING_TICKS) {
            if (ctx.isLookingAt(currentTarget.against)
                    && ctx.objectMouseOver() instanceof BlockHitResult
                    && ((BlockHitResult) ctx.objectMouseOver()).getDirection() == currentTarget.face) {
                state = State.PLACING;
                ticksInState = 0;
            } else if (ticksInState >= AIMING_TIMEOUT) {
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickPlacing() {
        if (!isTargetValid(currentTarget)) {
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, currentTarget.against, currentTarget.hitVec, blockReachDistance, false);
        if (rot.isPresent()) {
            currentRot = rot.get();
        }

        baritone.getLookBehavior().updateTarget(currentRot, true);

        if (currentTorchSlot != OFFHAND_SLOT) {
            ctx.player().getInventory().setSelectedSlot(currentTorchSlot);
        }
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);

        ticksInState++;
        if (ticksInState >= PLACING_TICKS) {
            lastTorchPos = ctx.playerFeet();
            warned = false;
            IPathExecutor current = baritone.getPathingBehavior().getCurrent();
            if (current != null) {
                lastTorchMovementIndex = current.getPosition();
                lastTorchPathExecutor = current;
            } else {
                lastTorchMovementIndex = -1;
                lastTorchPathExecutor = null;
            }
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private boolean isTargetValid(PlacementTarget target) {
        if (target == null) {
            return false;
        }
        BlockState wallState = ctx.world().getBlockState(target.against);
        Direction checkFace = (target.face == Direction.UP) ? Direction.UP : target.face.getOpposite();
        if (!wallState.isFaceSturdy(ctx.world(), target.against, checkFace)) {
            return false;
        }
        BlockPos torchPos = target.against.relative(target.face);
        return ctx.world().getBlockState(torchPos).getBlock() instanceof AirBlock;
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        currentTarget = null;
        currentRot = null;
        currentTorchSlot = -1;
    }

    private PlacementTarget findPlacementTarget(BlockPos feet) {
        TorchPlacementSide side = config.placementSide;

        if (side == TorchPlacementSide.LEFT || side == TorchPlacementSide.RIGHT) {
            Direction moveDir = getHorizontalMovementDirection();
            Direction wallDir = (side == TorchPlacementSide.LEFT)
                    ? moveDir.getCounterClockWise()
                    : moveDir.getClockWise();

            PlacementTarget target = tryWallPlacement(feet.above(), wallDir);
            if (target != null) {
                return target;
            }

            target = tryWallPlacement(feet.above(), wallDir.getOpposite());
            if (target != null) {
                return target;
            }

            target = tryWallPlacement(feet, wallDir);
            if (target != null) {
                return target;
            }

            target = tryWallPlacement(feet, wallDir.getOpposite());
            if (target != null) {
                return target;
            }
        }

        return tryFloorPlacement(feet);
    }

    private PlacementTarget tryWallPlacement(BlockPos torchPos, Direction wallDir) {
        BlockPos wallBlock = torchPos.relative(wallDir);
        BlockState wallState = ctx.world().getBlockState(wallBlock);
        Direction face = wallDir.getOpposite();
        if (!wallState.isFaceSturdy(ctx.world(), wallBlock, face)) {
            return null;
        }
        if (!(ctx.world().getBlockState(torchPos).getBlock() instanceof AirBlock)) {
            return null;
        }
        Vec3 hitVec = Vec3.atCenterOf(wallBlock)
                .add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
        return new PlacementTarget(wallBlock, face, hitVec);
    }

    private PlacementTarget tryFloorPlacement(BlockPos feet) {
        BlockPos below = feet.below();
        BlockState floorState = ctx.world().getBlockState(below);
        if (!floorState.isFaceSturdy(ctx.world(), below, Direction.UP)) {
            return null;
        }
        if (!(ctx.world().getBlockState(feet).getBlock() instanceof AirBlock)) {
            return null;
        }
        Vec3 hitVec = new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
        return new PlacementTarget(below, Direction.UP, hitVec);
    }

    private boolean shouldPlaceNow(BlockPos feet) {
        if (lastTorchPos == null) {
            return true;
        }

        int effectiveSpacing = computeEffectiveSpacing();

        IPathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null && current == lastTorchPathExecutor && lastTorchMovementIndex >= 0) {
            int currentIndex = current.getPosition();
            if (currentIndex >= lastTorchMovementIndex) {
                int pathDist = computePathDistance(current, lastTorchMovementIndex, currentIndex);
                return pathDist >= effectiveSpacing;
            }
        }

        double dist = Math.sqrt(lastTorchPos.distSqr(feet));
        return dist >= effectiveSpacing;
    }

    private int computeEffectiveSpacing() {
        int threshold = config.lightLevelThreshold;
        int safety = config.safetyMargin;
        int minSpacing = config.minSpacing;

        int maxReach = TORCH_LIGHT_RADIUS - threshold;

        double widthPenalty = 0;
        IPathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null) {
            double avgWidth = analyzeCorridorWidth(current);
            widthPenalty = Math.max(0, (avgWidth - 1)) * 0.5;
        }

        int calculatedSpacing = (int) (maxReach - safety - widthPenalty);
        return Math.max(calculatedSpacing, minSpacing);
    }

    private int computePathDistance(IPathExecutor executor, int fromIndex, int toIndex) {
        var movements = executor.getPath().movements();
        int totalDist = 0;
        int end = Math.min(toIndex, movements.size());
        for (int i = fromIndex; i < end; i++) {
            BlockPos dir = movements.get(i).getDirection();
            totalDist += Math.abs(dir.getX()) + Math.abs(dir.getY()) + Math.abs(dir.getZ());
        }
        return totalDist;
    }

    private double analyzeCorridorWidth(IPathExecutor executor) {
        int pos = executor.getPosition();
        var movements = executor.getPath().movements();
        if (pos >= movements.size()) {
            return 1;
        }

        int probeCount = 0;
        int totalWidth = 0;
        int samplesToTake = Math.min(5, movements.size() - pos);

        for (int i = 0; i < samplesToTake; i++) {
            int idx = pos + i;
            if (idx >= movements.size()) {
                break;
            }
            BlockPos dir = movements.get(idx).getDirection();
            int dx = dir.getX();
            int dz = dir.getZ();

            int perpX, perpZ;
            if (dx != 0 && dz == 0) {
                perpX = 0;
                perpZ = 1;
            } else if (dz != 0 && dx == 0) {
                perpX = 1;
                perpZ = 0;
            } else {
                continue;
            }

            BlockPos moveSrc = movements.get(idx).getSrc();
            int width = 1;
            for (int sign = -1; sign <= 1; sign += 2) {
                for (int d = 1; d <= 3; d++) {
                    BlockPos probe = moveSrc.offset(perpX * d * sign, 0, perpZ * d * sign);
                    if (ctx.world().getBlockState(probe).getBlock() instanceof AirBlock) {
                        width++;
                    } else {
                        break;
                    }
                }
            }
            totalWidth += width;
            probeCount++;
        }

        return probeCount > 0 ? (double) totalWidth / probeCount : 1;
    }

    private Direction getHorizontalMovementDirection() {
        IPathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null) {
            int pos = current.getPosition();
            var movements = current.getPath().movements();
            if (pos < movements.size()) {
                BlockPos dir = movements.get(pos).getDirection();
                int dx = dir.getX();
                int dz = dir.getZ();
                if (dx != 0 && dz == 0) {
                    return dx > 0 ? Direction.EAST : Direction.WEST;
                }
                if (dz != 0 && dx == 0) {
                    return dz > 0 ? Direction.SOUTH : Direction.NORTH;
                }
            }
        }
        return Direction.fromYRot(ctx.player().getYRot());
    }

    private int findTorchSlot() {
        if (ctx.player().getItemInHand(InteractionHand.OFF_HAND).getItem() == Items.TORCH) {
            return OFFHAND_SLOT;
        }
        for (int i = 0; i < 9; i++) {
            if (ctx.player().getInventory().getItem(i).getItem() == Items.TORCH) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onLostControl() {
        baritone.getInputOverrideHandler().clearAllKeys();
        resetState();
        lastTorchPos = null;
        lastTorchMovementIndex = -1;
        lastTorchPathExecutor = null;
        warned = false;
    }

    @Override
    public String displayName0() {
        return "Torch Placer";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 4;
    }

    private static class PlacementTarget {
        final BlockPos against;
        final Direction face;
        final Vec3 hitVec;

        PlacementTarget(BlockPos against, Direction face, Vec3 hitVec) {
            this.against = against;
            this.face = face;
            this.hitVec = hitVec;
        }
    }
}
