package sxy.baritoneextras.mobavoidance;

import baritone.api.IBaritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import sxy.baritoneextras.BaritoneExtras;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MobAvoidanceProcess implements IBaritoneProcess {

    private enum State { IDLE, ASSESSING, FLEEING, ENGAGING, SEEKING_COVER }

    private static final int ATTACK_COOLDOWN_TICKS = 10;
    private static final int MAX_FAILED_SWINGS = 3;
    private static final int COVER_SEARCH_RADIUS = 8;
    private static final double ENGAGE_REACH = 3.0;
    private static final double SPRINT_REACH = 6.0;

    private final IBaritone baritone;
    private final IPlayerContext ctx;
    private final MobAvoidanceConfig config;

    private State state = State.IDLE;
    private int ticksInState;
    private int scanCooldown;

    // Threat tracking
    private List<ThreatInfo> currentThreats = new ArrayList<>();

    // Combat state
    private Entity engageTarget;
    private float engageTargetLastHealth;
    private int swingsSinceLastDamage;
    private int attackCooldown;

    // Flee state
    private Goal savedGoal;
    private boolean hasSavedGoal;

    // Cover state
    private BlockPos coverTarget;
    private Entity coverFromEntity;

    // Goal-set tracking (prevents CANCEL_AND_SET_GOAL every tick)
    private boolean fleeGoalSet;
    private boolean engageGoalSet;
    private boolean coverGoalSet;

    // Scan throttling for active states
    private static final int ACTIVE_SCAN_INTERVAL = 10;
    private int activeScanCooldown;
    private List<ThreatInfo> cachedThreats = new ArrayList<>();

    public MobAvoidanceProcess(IBaritone baritone, MobAvoidanceConfig config) {
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
        // Already in an action state — stay active
        if (state != State.IDLE) {
            return true;
        }
        // Only activate if Baritone is doing something (another process is running)
        return baritone.getPathingBehavior().hasPath()
                || baritone.getPathingControlManager().mostRecentInControl().isPresent();
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        ticksInState++;

        if (!isSafeToCancel) {
            if (state == State.IDLE) {
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        switch (state) {
            case IDLE:
                return tickIdle();
            case ASSESSING:
                return tickAssessing();
            case FLEEING:
                return tickFleeing();
            case ENGAGING:
                return tickEngaging();
            case SEEKING_COVER:
                return tickSeekingCover();
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
        scanCooldown = config.scanIntervalTicks;

        List<ThreatInfo> threats = scanThreats();
        if (threats.isEmpty()) {
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        currentThreats = threats;
        state = State.ASSESSING;
        ticksInState = 0;
        BaritoneExtras.debugLog("MobAvoidance: IDLE -> ASSESSING | threats=" + threats.size());
        return tickAssessing();
    }

    // ── ASSESSING ─────────────────────────────────────────────────────────

    private PathingCommand tickAssessing() {
        if (currentThreats.isEmpty()) {
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        // Check for critical threats first (fusing creeper close by)
        for (ThreatInfo threat : currentThreats) {
            if (threat.critical) {
                saveCurrentGoal();
                state = State.FLEEING;
                ticksInState = 0;
                Helper.HELPER.logDirect("Mob Avoidance: CRITICAL threat! Fleeing from " + threat.type.name());
                BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> FLEEING | CRITICAL " + threat.type.name()
                        + " at distance=" + String.format("%.1f", threat.distance));
                return tickFleeing();
            }
        }

        float playerHealth = ctx.player().getHealth();
        float maxHealth = ctx.player().getMaxHealth();
        float healthPct = playerHealth / maxHealth;
        int threatCount = currentThreats.size();

        // Low health or many mobs → flee
        if (playerHealth <= config.retreatHealthThreshold || threatCount > config.engageMaxMobs) {
            saveCurrentGoal();
            state = State.FLEEING;
            ticksInState = 0;
            Helper.HELPER.logDirect("Mob Avoidance: Fleeing from " + threatCount + " threats");
            BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> FLEEING | health=" + String.format("%.1f", playerHealth)
                    + " threats=" + threatCount + " (threshold=" + config.retreatHealthThreshold
                    + ", maxMobs=" + config.engageMaxMobs + ")");
            return tickFleeing();
        }

        // Find the closest threat to decide response
        ThreatInfo closest = currentThreats.stream()
                .min(Comparator.comparingDouble(t -> t.distance))
                .orElse(null);
        if (closest == null) {
            resetState();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        ThreatType.Response response = closest.type.defaultResponse;

        // Override: enderman ignore (if enabled and not angry)
        if (closest.type == ThreatType.ENDERMAN && config.endermanIgnoreEnabled) {
            if (closest.entity instanceof EnderMan enderman && !enderman.isCreepy()) {
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
            // Angry enderman → flee
            response = ThreatType.Response.FLEE;
        }

        // Override: spider in daylight → ignore
        if (closest.type == ThreatType.SPIDER) {
            BlockPos mobPos = closest.entity.blockPosition();
            int lightLevel = ctx.world().getBrightness(LightLayer.SKY, mobPos);
            if (lightLevel > 11) {
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
        }

        switch (response) {
            case ENGAGE:
                if (config.engageEnabled && healthPct > 0.5f && threatCount <= config.engageMaxMobs) {
                    saveCurrentGoal();
                    engageTarget = closest.entity;
                    engageTargetLastHealth = (engageTarget instanceof LivingEntity le) ? le.getHealth() : 0;
                    swingsSinceLastDamage = 0;
                    attackCooldown = 0;
                    state = State.ENGAGING;
                    ticksInState = 0;
                    Helper.HELPER.logDirect("Mob Avoidance: Engaging " + closest.type.name());
                    BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> ENGAGING | target=" + closest.type.name()
                            + " distance=" + String.format("%.1f", closest.distance)
                            + " health=" + String.format("%.0f%%", healthPct * 100));
                    return tickEngaging();
                }
                // Can't or won't fight → flee
                saveCurrentGoal();
                state = State.FLEEING;
                ticksInState = 0;
                Helper.HELPER.logDirect("Mob Avoidance: Fleeing from " + closest.type.name());
                BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> FLEEING | response=ENGAGE but combat disabled/unsafe"
                        + " target=" + closest.type.name());
                return tickFleeing();

            case SEEK_COVER:
                if (closest.type == ThreatType.SKELETON && !config.skeletonCoverEnabled) {
                    saveCurrentGoal();
                    state = State.FLEEING;
                    ticksInState = 0;
                    Helper.HELPER.logDirect("Mob Avoidance: Fleeing from " + closest.type.name());
                    BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> FLEEING | cover disabled for " + closest.type.name());
                    return tickFleeing();
                }
                saveCurrentGoal();
                coverFromEntity = closest.entity;
                coverTarget = findCoverPosition(closest.entity);
                if (coverTarget != null) {
                    state = State.SEEKING_COVER;
                    ticksInState = 0;
                    Helper.HELPER.logDirect("Mob Avoidance: Seeking cover from " + closest.type.name());
                    BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> SEEKING_COVER | from=" + closest.type.name()
                            + " coverPos=" + coverTarget.toShortString());
                    return tickSeekingCover();
                }
                // No cover found → flee
                Helper.HELPER.logDirect("Mob Avoidance: Fleeing from " + closest.type.name() + " (no cover)");
                BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> FLEEING | no cover found from " + closest.type.name());
                state = State.FLEEING;
                ticksInState = 0;
                return tickFleeing();

            case FLEE:
                saveCurrentGoal();
                state = State.FLEEING;
                ticksInState = 0;
                Helper.HELPER.logDirect("Mob Avoidance: Fleeing from " + closest.type.name());
                BaritoneExtras.debugLog("MobAvoidance: ASSESSING -> FLEEING | response=FLEE target=" + closest.type.name()
                        + " distance=" + String.format("%.1f", closest.distance));
                return tickFleeing();

            case IGNORE:
            default:
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
        }
    }

    // ── FLEEING ───────────────────────────────────────────────────────────

    private PathingCommand tickFleeing() {
        // Reset on state entry (ticksInState is 0 at every transition)
        if (ticksInState == 0) {
            fleeGoalSet = false;
            activeScanCooldown = 0;
            baritone.getInputOverrideHandler().clearAllKeys();
        }

        // Throttle threat scanning — only re-scan every ACTIVE_SCAN_INTERVAL ticks
        List<ThreatInfo> threats;
        activeScanCooldown--;
        if (activeScanCooldown <= 0 || cachedThreats.isEmpty()) {
            threats = scanThreats();
            cachedThreats = threats;
            activeScanCooldown = ACTIVE_SCAN_INTERVAL;
        } else {
            threats = cachedThreats;
        }

        // Check if safe
        if (threats.isEmpty()) {
            Helper.HELPER.logDirect("Mob Avoidance: Safe. Resuming.");
            BaritoneExtras.debugLog("MobAvoidance: FLEEING -> IDLE | threats cleared");
            return resumeAndReset();
        }

        // Compute flee direction: away from threat centroid
        Vec3 playerPos = ctx.player().position();
        Vec3 centroid = Vec3.ZERO;
        for (ThreatInfo t : threats) {
            centroid = centroid.add(t.entity.position());
        }
        centroid = centroid.scale(1.0 / threats.size());

        Vec3 fleeVec = playerPos.subtract(centroid);
        if (fleeVec.horizontalDistanceSqr() < 0.01) {
            // Directly on top of mobs — pick a random direction
            fleeVec = new Vec3(1, 0, 0);
        }
        fleeVec = fleeVec.normalize();

        // Bias toward original goal direction if we have one
        if (hasSavedGoal && savedGoal instanceof GoalXZ goalXZ) {
            Vec3 goalVec = new Vec3(goalXZ.getX() - playerPos.x, 0, goalXZ.getZ() - playerPos.z).normalize();
            if (goalVec.dot(fleeVec) > -0.5) {
                fleeVec = fleeVec.add(goalVec.scale(0.3)).normalize();
            }
        }

        // Build flee positions for GoalRunAway
        BlockPos[] mobPositions = threats.stream()
                .map(t -> t.entity.blockPosition())
                .toArray(BlockPos[]::new);

        baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);

        Goal fleeGoal = new GoalRunAway(config.safeDistance, mobPositions);
        PathingCommandType cmdType = fleeGoalSet
                ? PathingCommandType.SET_GOAL_AND_PATH
                : PathingCommandType.CANCEL_AND_SET_GOAL;
        fleeGoalSet = true;
        return new PathingCommand(fleeGoal, cmdType);
    }

    // ── ENGAGING ──────────────────────────────────────────────────────────

    private PathingCommand tickEngaging() {
        // Reset on state entry
        if (ticksInState == 0) {
            engageGoalSet = false;
            activeScanCooldown = 0;
            baritone.getInputOverrideHandler().clearAllKeys();
        }

        // Target dead or removed?
        if (engageTarget == null || engageTarget.isRemoved()
                || (engageTarget instanceof LivingEntity le && le.isDeadOrDying())) {
            Helper.HELPER.logDirect("Mob Avoidance: Target eliminated. Resuming.");
            BaritoneExtras.debugLog("MobAvoidance: ENGAGING -> IDLE | target eliminated");
            return resumeAndReset();
        }

        // Health check — retreat if too low
        if (ctx.player().getHealth() <= config.retreatHealthThreshold) {
            Helper.HELPER.logDirect("Mob Avoidance: Low health! Switching to flee.");
            BaritoneExtras.debugLog("MobAvoidance: ENGAGING -> FLEEING | low health="
                    + String.format("%.1f", ctx.player().getHealth()));
            state = State.FLEEING;
            ticksInState = 0;
            engageTarget = null;
            return tickFleeing();
        }

        // Check for new threats that make fighting unwise (throttled)
        activeScanCooldown--;
        if (activeScanCooldown <= 0 || cachedThreats.isEmpty()) {
            cachedThreats = scanThreats();
            activeScanCooldown = ACTIVE_SCAN_INTERVAL;
        }
        long hostileCount = cachedThreats.stream().filter(t -> t.type.defaultResponse != ThreatType.Response.IGNORE).count();
        if (hostileCount > config.engageMaxMobs) {
            Helper.HELPER.logDirect("Mob Avoidance: Too many threats. Switching to flee.");
            BaritoneExtras.debugLog("MobAvoidance: ENGAGING -> FLEEING | too many threats=" + hostileCount
                    + " (max=" + config.engageMaxMobs + ")");
            state = State.FLEEING;
            ticksInState = 0;
            engageTarget = null;
            return tickFleeing();
        }

        // Check if swings aren't doing damage (invulnerable mob)
        if (swingsSinceLastDamage >= MAX_FAILED_SWINGS) {
            Helper.HELPER.logDirect("Mob Avoidance: Target seems invulnerable. Disengaging.");
            BaritoneExtras.debugLog("MobAvoidance: ENGAGING -> FLEEING | failed swings=" + swingsSinceLastDamage);
            state = State.FLEEING;
            ticksInState = 0;
            engageTarget = null;
            return tickFleeing();
        }

        double dist = ctx.player().distanceTo(engageTarget);

        // Look at target
        Vec3 eyePos = ctx.player().getEyePosition();
        Vec3 targetCenter = engageTarget.position().add(0, engageTarget.getBbHeight() / 2.0, 0);
        Rotation lookRot = RotationUtils.calcRotationFromVec3d(eyePos, targetCenter, ctx.playerRotations());
        baritone.getLookBehavior().updateTarget(lookRot, true);

        // Sprint toward if far
        if (dist > ENGAGE_REACH) {
            baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, dist > SPRINT_REACH);
            // Path toward the mob
            Goal attackGoal = new GoalBlock(engageTarget.blockPosition());
            PathingCommandType cmdType = engageGoalSet
                    ? PathingCommandType.SET_GOAL_AND_PATH
                    : PathingCommandType.CANCEL_AND_SET_GOAL;
            engageGoalSet = true;
            return new PathingCommand(attackGoal, cmdType);
        }

        // Within attack range — swing
        baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);
        attackCooldown--;
        if (attackCooldown <= 0) {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, true);
            attackCooldown = ATTACK_COOLDOWN_TICKS;

            // Check if we did damage
            if (engageTarget instanceof LivingEntity le) {
                float currentHealth = le.getHealth();
                if (currentHealth < engageTargetLastHealth) {
                    swingsSinceLastDamage = 0;
                } else {
                    swingsSinceLastDamage++;
                }
                engageTargetLastHealth = currentHealth;
            }
        } else {
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_LEFT, false);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    // ── SEEKING_COVER ─────────────────────────────────────────────────────

    private PathingCommand tickSeekingCover() {
        // Reset on state entry
        if (ticksInState == 0) {
            coverGoalSet = false;
            baritone.getInputOverrideHandler().clearAllKeys();
        }

        // Target gone?
        if (coverFromEntity == null || coverFromEntity.isRemoved()
                || (coverFromEntity instanceof LivingEntity le && le.isDeadOrDying())) {
            Helper.HELPER.logDirect("Mob Avoidance: Threat gone. Resuming.");
            BaritoneExtras.debugLog("MobAvoidance: SEEKING_COVER -> IDLE | threat gone");
            return resumeAndReset();
        }

        // Already in cover (line of sight broken)?
        if (!hasLineOfSight(coverFromEntity)) {
            // Wait a moment then resume
            if (ticksInState > 20) {
                Helper.HELPER.logDirect("Mob Avoidance: In cover. Resuming.");
                BaritoneExtras.debugLog("MobAvoidance: SEEKING_COVER -> IDLE | cover reached");
                return resumeAndReset();
            }
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Health check
        if (ctx.player().getHealth() <= config.retreatHealthThreshold) {
            state = State.FLEEING;
            ticksInState = 0;
            coverTarget = null;
            coverFromEntity = null;
            return tickFleeing();
        }

        // Try to reach cover target
        if (coverTarget == null) {
            coverTarget = findCoverPosition(coverFromEntity);
        }
        if (coverTarget == null) {
            // No cover — flee instead
            state = State.FLEEING;
            ticksInState = 0;
            coverFromEntity = null;
            return tickFleeing();
        }

        // Timeout — if we can't reach cover in 100 ticks, just flee
        if (ticksInState > 100) {
            BaritoneExtras.debugLog("MobAvoidance: SEEKING_COVER -> FLEEING | timeout after 100 ticks");
            state = State.FLEEING;
            ticksInState = 0;
            coverTarget = null;
            coverFromEntity = null;
            return tickFleeing();
        }

        baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, true);
        Goal coverGoal = new GoalBlock(coverTarget);
        PathingCommandType cmdType = coverGoalSet
                ? PathingCommandType.SET_GOAL_AND_PATH
                : PathingCommandType.CANCEL_AND_SET_GOAL;
        coverGoalSet = true;
        return new PathingCommand(coverGoal, cmdType);
    }

    // ── THREAT SCANNING ───────────────────────────────────────────────────

    private List<ThreatInfo> scanThreats() {
        List<ThreatInfo> threats = new ArrayList<>();
        AABB scanBox = ctx.player().getBoundingBox().inflate(config.scanRadius);
        List<Monster> mobs = ctx.world().getEntitiesOfClass(Monster.class, scanBox);

        float playerHealth = ctx.player().getHealth();
        float maxHealth = ctx.player().getMaxHealth();
        float healthPct = (maxHealth > 0) ? playerHealth / maxHealth : 1.0f;

        for (Monster mob : mobs) {
            if (mob.isDeadOrDying()) {
                continue;
            }

            ThreatType type = ThreatType.classify(mob);
            double distance = ctx.player().distanceTo(mob);

            // Skip if outside this type's avoidance radius
            if (distance > type.avoidanceRadius) {
                continue;
            }

            boolean hasLOS = hasLineOfSight(mob);

            // Skip mobs behind walls unless dangerously close
            if (!hasLOS && distance > 5) {
                continue;
            }

            boolean critical = false;

            // Creeper fuse detection
            if (type == ThreatType.CREEPER && mob instanceof Creeper creeper) {
                if (creeper.getSwellDir() > 0 && distance < 5) {
                    critical = true;
                }
                if (!config.creeperFleeEnabled && !critical) {
                    continue;
                }
            }

            // Enderman: only care if angry
            if (type == ThreatType.ENDERMAN && config.endermanIgnoreEnabled) {
                if (mob instanceof EnderMan enderman && !enderman.isCreepy()) {
                    continue;
                }
            }

            // Threat score
            int groupCount = Math.max(1, (int) mobs.stream()
                    .filter(m -> m.distanceTo(mob) < 8)
                    .count());
            double groupMultiplier = 1.0 + (groupCount - 1) * 0.3;
            double threatScore = (type.baseThreatScore * groupMultiplier) / Math.max(1, distance);
            threatScore *= (2.0 - healthPct); // More dangerous when player is hurt

            threats.add(new ThreatInfo(mob, type, distance, hasLOS, threatScore, critical));
        }

        threats.sort(Comparator.comparingDouble((ThreatInfo t) -> t.critical ? 0 : 1)
                .thenComparingDouble(t -> -t.threatScore));

        return threats;
    }

    private boolean hasLineOfSight(Entity mob) {
        Vec3 eyePos = ctx.player().getEyePosition();
        Vec3 mobCenter = mob.position().add(0, mob.getBbHeight() / 2.0, 0);
        ClipContext clipCtx = new ClipContext(
                eyePos, mobCenter,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                ctx.player()
        );
        BlockHitResult result = ctx.world().clip(clipCtx);
        return result.getType() == HitResult.Type.MISS;
    }

    // ── COVER FINDING ─────────────────────────────────────────────────────

    private BlockPos findCoverPosition(Entity threatEntity) {
        BlockPos playerPos = ctx.playerFeet();
        Vec3 threatPos = threatEntity.position();
        BlockPos bestCover = null;
        double bestScore = Double.MIN_VALUE;

        for (int dx = -COVER_SEARCH_RADIUS; dx <= COVER_SEARCH_RADIUS; dx++) {
            for (int dz = -COVER_SEARCH_RADIUS; dz <= COVER_SEARCH_RADIUS; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = playerPos.offset(dx, dy, dz);

                    // Must be standable (air at feet and head, solid below)
                    BlockState belowState = ctx.world().getBlockState(candidate.below());
                    if (!belowState.isFaceSturdy(ctx.world(), candidate.below(), net.minecraft.core.Direction.UP)) {
                        continue;
                    }
                    if (!ctx.world().getBlockState(candidate).isAir()) {
                        continue;
                    }
                    if (!ctx.world().getBlockState(candidate.above()).isAir()) {
                        continue;
                    }

                    // Check if this position blocks line of sight to the threat
                    Vec3 candidateEye = Vec3.atCenterOf(candidate).add(0, 0.6, 0);
                    Vec3 threatCenter = threatPos.add(0, threatEntity.getBbHeight() / 2.0, 0);
                    ClipContext clipCtx = new ClipContext(
                            candidateEye, threatCenter,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            ctx.player()
                    );
                    BlockHitResult result = ctx.world().clip(clipCtx);
                    if (result.getType() != HitResult.Type.BLOCK) {
                        continue; // Still has LOS — not good cover
                    }

                    // Score: prefer closer to player, farther from mob
                    double distFromPlayer = Math.sqrt(candidate.distSqr(playerPos));
                    double distFromMob = candidateEye.distanceTo(threatPos);
                    double score = distFromMob - distFromPlayer * 2;

                    if (score > bestScore) {
                        bestScore = score;
                        bestCover = candidate;
                    }
                }
            }
        }

        return bestCover;
    }

    // ── GOAL SAVE/RESTORE ─────────────────────────────────────────────────

    private void saveCurrentGoal() {
        if (!hasSavedGoal) {
            Goal current = baritone.getPathingBehavior().getGoal();
            if (current != null) {
                savedGoal = current;
                hasSavedGoal = true;
            }
        }
    }

    private PathingCommand resumeAndReset() {
        PathingCommand result;
        if (hasSavedGoal && savedGoal != null) {
            result = new PathingCommand(savedGoal, PathingCommandType.SET_GOAL_AND_PATH);
        } else {
            result = new PathingCommand(null, PathingCommandType.DEFER);
        }
        resetState();
        return result;
    }

    // ── STATE MANAGEMENT ──────────────────────────────────────────────────

    private void resetState() {
        baritone.getInputOverrideHandler().clearAllKeys();
        state = State.IDLE;
        ticksInState = 0;
        scanCooldown = 0;
        currentThreats.clear();
        engageTarget = null;
        engageTargetLastHealth = 0;
        swingsSinceLastDamage = 0;
        attackCooldown = 0;
        coverTarget = null;
        coverFromEntity = null;
        savedGoal = null;
        hasSavedGoal = false;
        fleeGoalSet = false;
        engageGoalSet = false;
        coverGoalSet = false;
        activeScanCooldown = 0;
        cachedThreats.clear();
    }

    @Override
    public void onLostControl() {
        baritone.getInputOverrideHandler().clearAllKeys();
        resetState();
    }

    @Override
    public String displayName0() {
        return "Mob Avoidance";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 5;
    }

    // ── INNER CLASSES ─────────────────────────────────────────────────────

    private static class ThreatInfo {
        final Entity entity;
        final ThreatType type;
        final double distance;
        final boolean hasLineOfSight;
        final double threatScore;
        final boolean critical;

        ThreatInfo(Entity entity, ThreatType type, double distance,
                   boolean hasLineOfSight, double threatScore, boolean critical) {
            this.entity = entity;
            this.type = type;
            this.distance = distance;
            this.hasLineOfSight = hasLineOfSight;
            this.threatScore = threatScore;
            this.critical = critical;
        }
    }
}
