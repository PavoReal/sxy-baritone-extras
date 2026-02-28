package sxy.baritoneextras.roomlighter;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
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
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class RoomLighterProcess implements IBaritoneProcess {

    private static final int OFFHAND_SLOT = -2;
    private static final int AIMING_TICKS = 5;
    private static final int AIMING_TIMEOUT = 30;
    private static final int PLACING_TICKS = 5;
    private static final int MAX_REPLANS = 3;
    private static final int APPROACH_TIMEOUT = 80;
    private static final int STUCK_TICKS = 20;
    private static final int APPROACH_GOAL_RADIUS = 4;

    public enum State {
        IDLE, SCANNING, PLANNING, PATHING, APPROACHING, AIMING, PLACING, DONE
    }

    private final IBaritone baritone;
    private final IPlayerContext ctx;
    private final RoomLighterConfig config;

    private State state = State.IDLE;
    private int ticksInState;
    private boolean dryRun;

    // Scan/plan results
    private RoomScanResult scanResult;
    private List<BlockPos> plannedPositions;
    private int currentIndex;
    private int successfulPlacements;
    private int skippedPositions;
    private int replanCount;

    // Placement state
    private PlacementTarget currentTarget;
    private Rotation currentRot;
    private int currentTorchSlot;

    // Approaching state: stuck detection and path re-issue throttle
    private BlockPos lastPlayerPos;
    private int ticksSinceLastMove;
    private boolean goalIssued;
    private int calcFailCount;

    public RoomLighterProcess(IBaritone baritone, RoomLighterConfig config) {
        this.baritone = baritone;
        this.ctx = baritone.getPlayerContext();
        this.config = config;
    }

    public void start(boolean dryRun) {
        if (state != State.IDLE) {
            Helper.HELPER.logDirect("Room lighter is already running. Use #lightroom stop first.");
            return;
        }
        this.dryRun = dryRun;
        this.state = State.SCANNING;
        this.ticksInState = 0;
        this.currentIndex = 0;
        this.successfulPlacements = 0;
        this.skippedPositions = 0;
        this.replanCount = 0;
        this.scanResult = null;
        this.plannedPositions = null;
    }

    public void stop() {
        if (state == State.IDLE) {
            Helper.HELPER.logDirect("Room lighter is not running.");
            return;
        }
        Helper.HELPER.logDirect("Room lighter stopped. Placed " + successfulPlacements + " torches.");
        baritone.getInputOverrideHandler().clearAllKeys();
        resetState();
    }

    public State getState() {
        return state;
    }

    public int getRemaining() {
        return plannedPositions == null ? 0 : plannedPositions.size() - currentIndex;
    }

    public int getTotalPlanned() {
        return plannedPositions == null ? 0 : plannedPositions.size();
    }

    public int getSuccessful() {
        return successfulPlacements;
    }

    @Override
    public boolean isActive() {
        if (ctx.player() == null || ctx.world() == null) {
            return false;
        }
        return state != State.IDLE;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        if (state != State.PLACING) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        }

        switch (state) {
            case SCANNING:
                return tickScanning();
            case PLANNING:
                return tickPlanning();
            case PATHING:
                return tickPathing(calcFailed);
            case APPROACHING:
                return tickApproaching(calcFailed);
            case AIMING:
                return tickAiming();
            case PLACING:
                return tickPlacing();
            case DONE:
                return tickDone();
            default:
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
        }
    }

    private PathingCommand tickScanning() {
        BlockPos feet = ctx.playerFeet();
        scanResult = RoomScanner.scan(ctx.world(), feet, config.maxRadius, config.maxVolume);

        Helper.HELPER.logDirect("Room scan: " + scanResult.airBlocks.size() + " air blocks, "
                + scanResult.floorBlocks.size() + " floor blocks");
        if (scanResult.cappedByRadius) {
            Helper.HELPER.logDirect("Warning: Scan reached max radius (" + config.maxRadius + ")");
        }
        if (scanResult.cappedByVolume) {
            Helper.HELPER.logDirect("Warning: Scan reached max volume (" + config.maxVolume + ")");
        }

        state = State.PLANNING;
        ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickPlanning() {
        BlockPos feet = ctx.playerFeet();
        plannedPositions = TorchPlanner.plan(ctx.world(), scanResult.floorBlocks,
                config.lightLevelThreshold, feet);

        if (plannedPositions.isEmpty()) {
            Helper.HELPER.logDirect("Room is already fully lit!");
            state = State.DONE;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        Helper.HELPER.logDirect("Planned " + plannedPositions.size() + " torch placements");

        if (dryRun) {
            Helper.HELPER.logDirect("Dry run complete. Use #lightroom to execute.");
            state = State.DONE;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        state = State.PATHING;
        ticksInState = 0;
        currentIndex = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickPathing(boolean calcFailed) {
        // Skip positions that are now lit (torches placed earlier in this run)
        while (currentIndex < plannedPositions.size()) {
            BlockPos pos = plannedPositions.get(currentIndex);
            if (ctx.world().getBrightness(LightLayer.BLOCK, pos) >= config.lightLevelThreshold) {
                skippedPositions++;
                currentIndex++;
            } else {
                break;
            }
        }

        if (currentIndex >= plannedPositions.size()) {
            // Re-plan to catch dark spots missed by the theoretical model (e.g. behind walls)
            if (replanCount < MAX_REPLANS) {
                List<BlockPos> newPlan = TorchPlanner.plan(ctx.world(), scanResult.floorBlocks,
                        config.lightLevelThreshold, ctx.playerFeet());
                if (!newPlan.isEmpty()) {
                    replanCount++;
                    Helper.HELPER.logDirect("Re-planned " + newPlan.size()
                            + " additional torch placements (pass " + (replanCount + 1) + ")");
                    plannedPositions = newPlan;
                    currentIndex = 0;
                    return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                }
            }
            state = State.DONE;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Re-sort remaining positions by distance from current player position to prevent zigzag
        if (currentIndex < plannedPositions.size() - 1) {
            BlockPos feet = ctx.playerFeet();
            List<BlockPos> remaining = plannedPositions.subList(currentIndex, plannedPositions.size());
            remaining.sort(Comparator.comparingDouble(pos -> pos.distSqr(feet)));
        }

        // Check torch availability
        int torchSlot = findTorchSlot();
        if (torchSlot == -1) {
            Helper.HELPER.logDirect("Out of torches! Placed " + successfulPlacements
                    + "/" + plannedPositions.size());
            state = State.DONE;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        BlockPos target = plannedPositions.get(currentIndex);
        BlockPos against = target.below();
        Vec3 hitVec = new Vec3(against.getX() + 0.5, against.getY() + 1.0, against.getZ() + 0.5);

        // Check if already within reach
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, against, hitVec, blockReachDistance, false);
        if (rot.isPresent()) {
            currentTarget = new PlacementTarget(against, Direction.UP, hitVec);
            currentRot = rot.get();
            currentTorchSlot = torchSlot;
            state = State.AIMING;
            ticksInState = 0;
            baritone.getLookBehavior().updateTarget(currentRot, true);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (calcFailed) {
            Helper.HELPER.logDirect("Cannot path to torch position, skipping");
            skippedPositions++;
            currentIndex++;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Path toward the target
        state = State.APPROACHING;
        ticksInState = 0;
        lastPlayerPos = ctx.playerFeet();
        ticksSinceLastMove = 0;
        goalIssued = false;
        calcFailCount = 0;
        return new PathingCommand(
                new GoalNear(target, APPROACH_GOAL_RADIUS),
                PathingCommandType.SET_GOAL_AND_PATH
        );
    }

    private PathingCommand tickApproaching(boolean calcFailed) {
        BlockPos target = plannedPositions.get(currentIndex);
        BlockPos against = target.below();
        Vec3 hitVec = new Vec3(against.getX() + 0.5, against.getY() + 1.0, against.getZ() + 0.5);

        // Check if now within reach
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, against, hitVec, blockReachDistance, false);
        if (rot.isPresent()) {
            int torchSlot = findTorchSlot();
            if (torchSlot == -1) {
                Helper.HELPER.logDirect("Out of torches! Placed " + successfulPlacements
                        + "/" + plannedPositions.size());
                state = State.DONE;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            currentTarget = new PlacementTarget(against, Direction.UP, hitVec);
            currentRot = rot.get();
            currentTorchSlot = torchSlot;
            state = State.AIMING;
            ticksInState = 0;
            baritone.getLookBehavior().updateTarget(currentRot, true);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Track player movement for stuck detection
        BlockPos currentFeet = ctx.playerFeet();
        if (currentFeet.equals(lastPlayerPos)) {
            ticksSinceLastMove++;
        } else {
            lastPlayerPos = currentFeet;
            ticksSinceLastMove = 0;
        }

        // If Baritone is still pathing, let it continue
        if (baritone.getPathingBehavior().hasPath()) {
            goalIssued = true;
            return new PathingCommand(
                    new GoalNear(target, APPROACH_GOAL_RADIUS),
                    PathingCommandType.SET_GOAL_AND_PATH
            );
        }

        // Baritone stopped pathing — check why
        if (calcFailed) {
            calcFailCount++;
            // Allow one retry before giving up — a single recalc failure doesn't mean unreachable
            if (calcFailCount >= 2) {
                skippedPositions++;
                currentIndex++;
                state = State.PATHING;
                ticksInState = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            // Retry: re-issue the goal
            goalIssued = false;
        }

        ticksInState++;

        // Stuck detection: if player hasn't moved in STUCK_TICKS, skip immediately
        if (ticksSinceLastMove >= STUCK_TICKS && ticksInState > STUCK_TICKS) {
            skippedPositions++;
            currentIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Overall timeout
        if (ticksInState > APPROACH_TIMEOUT) {
            skippedPositions++;
            currentIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Issue goal once, then let Baritone calculate without re-issuing every tick
        if (!goalIssued) {
            goalIssued = true;
            return new PathingCommand(
                    new GoalNear(target, APPROACH_GOAL_RADIUS),
                    PathingCommandType.SET_GOAL_AND_PATH
            );
        }

        // Goal already issued, wait for Baritone to calculate
        return new PathingCommand(
                new GoalNear(target, APPROACH_GOAL_RADIUS),
                PathingCommandType.DEFER
        );
    }

    private PathingCommand tickAiming() {
        if (!isTargetValid(currentTarget)) {
            skippedPositions++;
            currentIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, currentTarget.against, currentTarget.hitVec, blockReachDistance, false);
        if (!rot.isPresent()) {
            // Reach failed — go back to APPROACHING instead of skipping entirely.
            // The player may have drifted out of range; re-approaching is better than giving up.
            state = State.APPROACHING;
            ticksInState = 0;
            lastPlayerPos = ctx.playerFeet();
            ticksSinceLastMove = 0;
            goalIssued = false;
            calcFailCount = 0;
            return new PathingCommand(
                    new GoalNear(plannedPositions.get(currentIndex), APPROACH_GOAL_RADIUS),
                    PathingCommandType.SET_GOAL_AND_PATH
            );
        }
        currentRot = rot.get();

        baritone.getLookBehavior().updateTarget(currentRot, true);
        ticksInState++;

        if (ticksInState >= AIMING_TICKS) {
            // Relaxed check: accept looking at the target block regardless of which face
            // the crosshair hits. The block interaction system will handle face selection.
            if (ctx.isLookingAt(currentTarget.against)) {
                state = State.PLACING;
                ticksInState = 0;
            } else if (ticksInState >= AIMING_TIMEOUT) {
                skippedPositions++;
                currentIndex++;
                state = State.PATHING;
                ticksInState = 0;
            }
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickPlacing() {
        if (!isTargetValid(currentTarget)) {
            skippedPositions++;
            currentIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
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
            // Verify torch was actually placed before counting success
            BlockPos torchPos = currentTarget.against.relative(currentTarget.face);
            if (ctx.world().getBlockState(torchPos).getBlock() instanceof TorchBlock) {
                successfulPlacements++;
            } else {
                skippedPositions++;
            }
            currentIndex++;
            state = State.PATHING;
            ticksInState = 0;
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickDone() {
        int total = plannedPositions == null ? 0 : plannedPositions.size();
        if (!dryRun && total > 0) {
            Helper.HELPER.logDirect("Room lighting complete! Placed " + successfulPlacements
                    + "/" + total + " torches"
                    + (skippedPositions > 0 ? " (" + skippedPositions + " skipped)" : ""));
        }
        resetState();
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private boolean isTargetValid(PlacementTarget target) {
        if (target == null) {
            return false;
        }
        BlockState floorState = ctx.world().getBlockState(target.against);
        if (!floorState.isFaceSturdy(ctx.world(), target.against, Direction.UP)) {
            return false;
        }
        BlockPos torchPos = target.against.relative(target.face);
        return ctx.world().getBlockState(torchPos).getBlock() instanceof AirBlock;
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

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        dryRun = false;
        scanResult = null;
        plannedPositions = null;
        currentIndex = 0;
        successfulPlacements = 0;
        skippedPositions = 0;
        replanCount = 0;
        currentTarget = null;
        currentRot = null;
        currentTorchSlot = -1;
        lastPlayerPos = null;
        ticksSinceLastMove = 0;
        goalIssued = false;
        calcFailCount = 0;
    }

    @Override
    public void onLostControl() {
        baritone.getInputOverrideHandler().clearAllKeys();
        resetState();
    }

    @Override
    public String displayName0() {
        return "Room Lighter";
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public double priority() {
        return 2;
    }
}
