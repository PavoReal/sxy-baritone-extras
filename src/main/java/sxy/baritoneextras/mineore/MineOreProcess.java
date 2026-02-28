package sxy.baritoneextras.mineore;

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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import sxy.baritoneextras.BaritoneExtras;

import java.util.List;
import java.util.Optional;

public final class MineOreProcess implements IBaritoneProcess {

    private enum State {
        IDLE, SCANNING, EQUIPPING, PATHING, APPROACHING, AIMING, BREAKING, COLLECTING, NEXT_BLOCK
    }

    private static final int AIMING_MIN_TICKS = 2;
    private static final int AIMING_TIMEOUT = 15;
    private static final int BREAK_TIMEOUT = 200;
    private static final int APPROACH_TIMEOUT = 80;
    private static final int STUCK_TICKS = 20;
    private static final int APPROACH_GOAL_RADIUS = 2;
    private static final int COLLECT_TIMEOUT = 40;
    private static final int DONE_COOLDOWN = 20;

    private final IBaritone baritone;
    private final IPlayerContext ctx;
    private final MineOreConfig config;

    private State state = State.IDLE;
    private int ticksInState;
    private int scanCooldown;

    // Vein data
    private List<BlockPos> veinBlocks;
    private OreType veinType;
    private int veinIndex;
    private int minedCount;

    // Equip state
    private int previousSlot = -1;

    // Approaching state
    private BlockPos lastPlayerPos;
    private int ticksSinceLastMove;
    private boolean goalIssued;
    private int calcFailCount;

    // Aiming state
    private Rotation currentRot;

    // Collecting state
    private BlockPos collectTarget;

    public MineOreProcess(IBaritone baritone, MineOreConfig config) {
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
            if (state != State.IDLE) {
                resetState();
            }
            return false;
        }
        // Stay active while mid-action
        if (state != State.IDLE) {
            return true;
        }
        // Only scan when Baritone is actively pathing
        return baritone.getPathingBehavior().hasPath();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        // Clear left-click when not breaking
        if (state != State.BREAKING) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        }

        switch (state) {
            case IDLE:
                return tickIdle();
            case SCANNING:
                return tickScanning();
            case EQUIPPING:
                return tickEquipping();
            case PATHING:
                return tickPathing(calcFailed);
            case APPROACHING:
                return tickApproaching(calcFailed);
            case AIMING:
                return tickAiming();
            case BREAKING:
                return tickBreaking();
            case COLLECTING:
                return tickCollecting();
            case NEXT_BLOCK:
                return tickNextBlock();
            default:
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
        }
    }

    // ── IDLE ──────────────────────────────────────────────────────────────

    private PathingCommand tickIdle() {
        scanCooldown--;
        if (scanCooldown > 0) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        scanCooldown = config.scanInterval;

        BlockPos playerPos = ctx.playerFeet();
        int radius = config.scanRadius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = playerPos.offset(dx, dy, dz);
                    Block block = ctx.world().getBlockState(pos).getBlock();
                    OreType type = OreType.classify(block);
                    if (type != null && config.isOreEnabled(type) && isExposed(pos)) {
                        // Found exposed ore — start scanning the vein
                        veinType = type;
                        veinBlocks = null;
                        veinIndex = 0;
                        minedCount = 0;
                        state = State.SCANNING;
                        ticksInState = 0;
                        BaritoneExtras.debugLog("MineOre: IDLE -> SCANNING | found exposed "
                                + type.configKey + " at " + pos.toShortString());
                        // Store the start block for BFS
                        lastPlayerPos = pos; // reuse field temporarily as scan start
                        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
                    }
                }
            }
        }

        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    // ── SCANNING ──────────────────────────────────────────────────────────

    private PathingCommand tickScanning() {
        BlockPos scanStart = lastPlayerPos; // stored from IDLE
        veinBlocks = VeinScanner.scanVein(ctx.world(), scanStart, veinType);

        if (veinBlocks.isEmpty()) {
            BaritoneExtras.debugLog("MineOre: SCANNING -> IDLE | vein empty after BFS");
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        Helper.HELPER.logDirect("Mine Ore: Found " + veinType.configKey + " vein ("
                + veinBlocks.size() + " blocks)");
        BaritoneExtras.debugLog("MineOre: SCANNING -> EQUIPPING | vein=" + veinType.configKey
                + " size=" + veinBlocks.size());

        state = State.EQUIPPING;
        ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ── EQUIPPING ─────────────────────────────────────────────────────────

    private PathingCommand tickEquipping() {
        previousSlot = ctx.player().getInventory().getSelectedSlot();
        int pickSlot = findBestPickaxe();

        if (pickSlot == -1) {
            Helper.HELPER.logDirect("Mine Ore: No pickaxe in hotbar, skipping vein");
            BaritoneExtras.debugLog("MineOre: EQUIPPING -> IDLE | no pickaxe");
            scanCooldown = DONE_COOLDOWN;
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        ctx.player().getInventory().setSelectedSlot(pickSlot);
        BaritoneExtras.debugLog("MineOre: Equipped pickaxe in slot " + pickSlot);

        state = State.PATHING;
        ticksInState = 0;
        veinIndex = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ── PATHING ───────────────────────────────────────────────────────────

    private PathingCommand tickPathing(boolean calcFailed) {
        BlockPos playerPos = ctx.playerFeet();
        int maxDistSq = (config.scanRadius + 2) * (config.scanRadius + 2);

        // Skip already-mined blocks and blocks too far from the player
        while (veinIndex < veinBlocks.size()) {
            BlockPos pos = veinBlocks.get(veinIndex);
            Block block = ctx.world().getBlockState(pos).getBlock();
            OreType type = OreType.classify(block);
            if (type == veinType && pos.distSqr(playerPos) <= maxDistSq) {
                break;
            }
            veinIndex++;
        }

        if (veinIndex >= veinBlocks.size()) {
            // Vein complete — go collect drops
            state = State.COLLECTING;
            ticksInState = 0;
            return startCollecting();
        }

        BlockPos target = veinBlocks.get(veinIndex);
        Vec3 hitVec = Vec3.atCenterOf(target);

        // Check if already within reach
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, target, hitVec, blockReachDistance, false);
        if (rot.isPresent()) {
            currentRot = rot.get();
            state = State.AIMING;
            ticksInState = 0;
            baritone.getLookBehavior().updateTarget(currentRot, true);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        if (calcFailed) {
            BaritoneExtras.debugLog("MineOre: Cannot path to ore block, skipping");
            veinIndex++;
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

    // ── APPROACHING ───────────────────────────────────────────────────────

    private PathingCommand tickApproaching(boolean calcFailed) {
        BlockPos target = veinBlocks.get(veinIndex);
        Vec3 hitVec = Vec3.atCenterOf(target);

        // Check if now within reach
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, target, hitVec, blockReachDistance, false);
        if (rot.isPresent()) {
            currentRot = rot.get();
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
            if (calcFailCount >= 2) {
                veinIndex++;
                state = State.PATHING;
                ticksInState = 0;
                return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
            }
            goalIssued = false;
        }

        ticksInState++;

        // Stuck detection
        if (ticksSinceLastMove >= STUCK_TICKS && ticksInState > STUCK_TICKS) {
            veinIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Overall timeout
        if (ticksInState > APPROACH_TIMEOUT) {
            veinIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Issue goal once, then let Baritone calculate
        if (!goalIssued) {
            goalIssued = true;
            return new PathingCommand(
                    new GoalNear(target, APPROACH_GOAL_RADIUS),
                    PathingCommandType.SET_GOAL_AND_PATH
            );
        }

        return new PathingCommand(
                new GoalNear(target, APPROACH_GOAL_RADIUS),
                PathingCommandType.DEFER
        );
    }

    // ── AIMING ────────────────────────────────────────────────────────────

    private PathingCommand tickAiming() {
        BlockPos target = veinBlocks.get(veinIndex);

        // Verify block is still ore
        Block block = ctx.world().getBlockState(target).getBlock();
        if (OreType.classify(block) != veinType) {
            veinIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        Vec3 hitVec = Vec3.atCenterOf(target);
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, target, hitVec, blockReachDistance, false);
        if (!rot.isPresent()) {
            // Lost reach — go back to APPROACHING
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
        currentRot = rot.get();

        baritone.getLookBehavior().updateTarget(currentRot, true);
        ticksInState++;

        if (ticksInState >= AIMING_MIN_TICKS) {
            if (ctx.isLookingAt(target)) {
                state = State.BREAKING;
                ticksInState = 0;
                BaritoneExtras.debugLog("MineOre: AIMING -> BREAKING | target=" + target.toShortString());
            } else if (ticksInState >= AIMING_TIMEOUT) {
                // Skip this block
                veinIndex++;
                state = State.PATHING;
                ticksInState = 0;
            }
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ── BREAKING ──────────────────────────────────────────────────────────

    private PathingCommand tickBreaking() {
        BlockPos target = veinBlocks.get(veinIndex);

        // Keep looking at the block
        Vec3 hitVec = Vec3.atCenterOf(target);
        double blockReachDistance = ctx.playerController().getBlockReachDistance();
        Optional<Rotation> rot = RotationUtils.reachableOffset(
                ctx, target, hitVec, blockReachDistance, false);
        if (rot.isPresent()) {
            currentRot = rot.get();
        }
        baritone.getLookBehavior().updateTarget(currentRot, true);

        // Hold left click to mine
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);

        ticksInState++;

        // Check if block is broken (no longer the ore type)
        Block block = ctx.world().getBlockState(target).getBlock();
        if (OreType.classify(block) != veinType) {
            minedCount++;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            BaritoneExtras.debugLog("MineOre: Mined " + veinType.configKey + " at "
                    + target.toShortString() + " (" + minedCount + "/" + veinBlocks.size() + ")");

            // Try to flow directly to the next reachable block without going through PATHING
            return advanceToNextBlock(blockReachDistance);
        }

        // Break timeout
        if (ticksInState > BREAK_TIMEOUT) {
            BaritoneExtras.debugLog("MineOre: BREAKING timeout, skipping block");
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
            veinIndex++;
            state = State.PATHING;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    /**
     * After mining a block, advance to the next vein block. If the next block is
     * already within reach, skip straight to AIMING. Otherwise fall back to PATHING.
     */
    private PathingCommand advanceToNextBlock(double blockReachDistance) {
        BlockPos playerPos = ctx.playerFeet();
        int maxDistSq = (config.scanRadius + 2) * (config.scanRadius + 2);
        veinIndex++;

        // Scan forward for the next valid block
        while (veinIndex < veinBlocks.size()) {
            BlockPos pos = veinBlocks.get(veinIndex);
            Block blk = ctx.world().getBlockState(pos).getBlock();
            if (OreType.classify(blk) == veinType && pos.distSqr(playerPos) <= maxDistSq) {
                break;
            }
            veinIndex++;
        }

        if (veinIndex >= veinBlocks.size()) {
            // Vein complete — go collect drops
            state = State.COLLECTING;
            ticksInState = 0;
            return startCollecting();
        }

        BlockPos next = veinBlocks.get(veinIndex);
        Vec3 nextHit = Vec3.atCenterOf(next);
        Optional<Rotation> nextRot = RotationUtils.reachableOffset(
                ctx, next, nextHit, blockReachDistance, false);
        if (nextRot.isPresent()) {
            // Within reach — go straight to AIMING
            currentRot = nextRot.get();
            state = State.AIMING;
            ticksInState = 0;
            baritone.getLookBehavior().updateTarget(currentRot, true);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Not in reach — use normal PATHING flow
        state = State.PATHING;
        ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ── COLLECTING (walk to drops) ───────────────────────────────────────

    /**
     * Compute the centroid of all mined blocks and start pathing there.
     */
    private PathingCommand startCollecting() {
        if (minedCount == 0 || veinBlocks == null || veinBlocks.isEmpty()) {
            state = State.NEXT_BLOCK;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Centroid of the mined blocks (first minedCount entries were all mined
        // but veinIndex was advanced past them; use all blocks up to veinBlocks.size()
        // that are no longer ore)
        int cx = 0, cy = 0, cz = 0, count = 0;
        for (BlockPos pos : veinBlocks) {
            Block block = ctx.world().getBlockState(pos).getBlock();
            if (OreType.classify(block) != veinType) {
                cx += pos.getX();
                cy += pos.getY();
                cz += pos.getZ();
                count++;
            }
        }
        if (count == 0) {
            state = State.NEXT_BLOCK;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        collectTarget = new BlockPos(cx / count, cy / count, cz / count);
        goalIssued = false;

        BaritoneExtras.debugLog("MineOre: COLLECTING | walking to drops at " + collectTarget.toShortString());

        return new PathingCommand(
                new GoalNear(collectTarget, 1),
                PathingCommandType.SET_GOAL_AND_PATH
        );
    }

    private PathingCommand tickCollecting() {
        ticksInState++;

        // Close enough to the drop area?
        if (collectTarget != null && ctx.playerFeet().distSqr(collectTarget) <= 4) {
            BaritoneExtras.debugLog("MineOre: COLLECTING -> NEXT_BLOCK | reached drops");
            state = State.NEXT_BLOCK;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Timeout — don't get stuck chasing drops forever
        if (ticksInState > COLLECT_TIMEOUT) {
            BaritoneExtras.debugLog("MineOre: COLLECTING timeout -> NEXT_BLOCK");
            state = State.NEXT_BLOCK;
            ticksInState = 0;
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Keep pathing to drop location
        if (collectTarget != null) {
            return new PathingCommand(
                    new GoalNear(collectTarget, 1),
                    PathingCommandType.SET_GOAL_AND_PATH
            );
        }

        state = State.NEXT_BLOCK;
        ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ── NEXT_BLOCK (vein complete) ────────────────────────────────────────

    private PathingCommand tickNextBlock() {
        // Restore previous hotbar slot
        if (previousSlot >= 0 && previousSlot <= 8 && ctx.player() != null) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }

        Helper.HELPER.logDirect("Mine Ore: Vein complete! Mined " + minedCount + "/" + veinBlocks.size()
                + " " + veinType.configKey + " blocks");
        BaritoneExtras.debugLog("MineOre: Vein done | mined=" + minedCount + " total=" + veinBlocks.size());

        scanCooldown = DONE_COOLDOWN;
        resetState();
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    // ── PICKAXE MANAGEMENT ────────────────────────────────────────────────

    private int findBestPickaxe() {
        int bestSlot = -1;
        int bestScore = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = ctx.player().getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.is(ItemTags.PICKAXES)) continue;

            // Score by tier: netherite > diamond > iron > stone > gold > wood
            String name = stack.getItem().toString().toLowerCase();
            int score;
            if (name.contains("netherite")) {
                score = 60;
            } else if (name.contains("diamond")) {
                score = 50;
            } else if (name.contains("iron")) {
                score = 40;
            } else if (name.contains("stone")) {
                score = 30;
            } else if (name.contains("gold")) {
                score = 20;
            } else {
                score = 10; // wood or other
            }

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // ── ORE DETECTION ──────────────────────────────────────────────────────

    /**
     * Returns true if the ore block has at least one adjacent air block,
     * meaning it's been uncovered/exposed and is visible to the player.
     */
    private boolean isExposed(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (ctx.world().getBlockState(pos.relative(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    // ── STATE MANAGEMENT ──────────────────────────────────────────────────

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        veinBlocks = null;
        veinType = null;
        veinIndex = 0;
        minedCount = 0;
        previousSlot = -1;
        lastPlayerPos = null;
        ticksSinceLastMove = 0;
        goalIssued = false;
        calcFailCount = 0;
        currentRot = null;
        collectTarget = null;
    }

    @Override
    public void onLostControl() {
        baritone.getInputOverrideHandler().clearAllKeys();
        resetState();
    }

    @Override
    public String displayName0() {
        return "Mine Ore";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 3;
    }
}
