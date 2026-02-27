package sxy.baritoneextras.autoeater;

import baritone.api.IBaritone;
import baritone.api.behavior.IPathingBehavior;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.input.Input;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoEaterProcess implements IBaritoneProcess {

    private static final int EAT_TIMEOUT_TICKS = 40;
    private static final int PAUSE_REEVALUATE_INTERVAL = 10;
    private static final int LOOKAHEAD_MOVEMENTS = 4;

    private enum State { IDLE, SELECTING, EATING, FINISHING }

    private final IBaritone baritone;
    private final IPlayerContext ctx;
    private final AutoEaterConfig config;

    private State state = State.IDLE;
    private int ticksInState;
    private int previousSlot = -1;
    private int foodSlot = -1;
    private boolean pausePathing;
    private boolean wasUsingItem;
    private boolean warned;

    public AutoEaterProcess(IBaritone baritone, AutoEaterConfig config) {
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
                onLostControl();
            }
            return false;
        }
        // Stay active while mid-action
        if (state != State.IDLE) {
            return true;
        }
        if (!baritone.getPathingBehavior().hasPath()) {
            return false;
        }
        if (ctx.player().getFoodData().getFoodLevel() >= config.hungerThreshold) {
            return false;
        }
        return findBestFoodSlot() != -1;
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        switch (state) {
            case IDLE:
                return tickIdle();
            case SELECTING:
                return tickSelecting();
            case EATING:
                return tickEating(isSafeToCancel);
            case FINISHING:
                return tickFinishing();
            default:
                resetState();
                return new PathingCommand(null, PathingCommandType.DEFER);
        }
    }

    private PathingCommand tickIdle() {
        int slot = findBestFoodSlot();
        if (slot == -1) {
            if (!warned) {
                Helper.HELPER.logDirect("AutoEater: No suitable food in hotbar");
                warned = true;
            }
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        warned = false;
        foodSlot = slot;
        pausePathing = shouldPausePathing();
        state = State.SELECTING;
        ticksInState = 0;
        return pausePathing
                ? new PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                : new PathingCommand(null, PathingCommandType.DEFER);
    }

    private PathingCommand tickSelecting() {
        previousSlot = ctx.player().getInventory().getSelectedSlot();
        ctx.player().getInventory().setSelectedSlot(foodSlot);
        state = State.EATING;
        ticksInState = 0;
        wasUsingItem = false;
        return pausePathing
                ? new PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                : new PathingCommand(null, PathingCommandType.DEFER);
    }

    private PathingCommand tickEating(boolean isSafeToCancel) {
        ticksInState++;

        // Safety: if Baritone urgently needs control and we're pausing, abort
        if (!isSafeToCancel && pausePathing) {
            abortEating();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        // Detect eating completion: player was using item and stopped
        boolean usingItem = ctx.player().isUsingItem();
        if (wasUsingItem && !usingItem) {
            state = State.FINISHING;
            ticksInState = 0;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return new PathingCommand(null, PathingCommandType.DEFER);
        }
        wasUsingItem = usingItem;

        // Verify held item is still food (stack may have been consumed)
        ItemStack held = ctx.player().getInventory().getItem(foodSlot);
        if (held.get(DataComponents.FOOD) == null) {
            state = State.FINISHING;
            ticksInState = 0;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        // Force right-click to eat
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);

        // Re-evaluate pause decision periodically
        if (ticksInState % PAUSE_REEVALUATE_INTERVAL == 0) {
            pausePathing = shouldPausePathing();
        }

        // Timeout safety net
        if (ticksInState >= EAT_TIMEOUT_TICKS) {
            state = State.FINISHING;
            ticksInState = 0;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        return pausePathing
                ? new PathingCommand(null, PathingCommandType.REQUEST_PAUSE)
                : new PathingCommand(null, PathingCommandType.DEFER);
    }

    private PathingCommand tickFinishing() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        if (previousSlot >= 0 && previousSlot <= 8) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }
        resetState();
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private void abortEating() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        if (previousSlot >= 0 && previousSlot <= 8) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }
        resetState();
    }

    private boolean shouldPausePathing() {
        if (!config.eatWhileWalking) {
            return true;
        }
        IPathingBehavior pathingBehavior = baritone.getPathingBehavior();
        IPathExecutor current = pathingBehavior.getCurrent();
        if (current == null) {
            return true;
        }
        int pos = current.getPosition();
        var movements = current.getPath().movements();
        for (int i = 0; i < LOOKAHEAD_MOVEMENTS && pos + i < movements.size(); i++) {
            BlockPos dir = movements.get(pos + i).getDirection();
            if (dir.getY() != 0) {
                return true;
            }
        }
        return false;
    }

    private int findBestFoodSlot() {
        int bestSlot = -1;
        float bestScore = -1;
        int foodLevel = ctx.player().getFoodData().getFoodLevel();

        for (int i = 0; i < 9; i++) {
            ItemStack stack = ctx.player().getInventory().getItem(i);
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) {
                continue;
            }
            // Skip chorus fruit (teleportation risk)
            if (stack.getItem() == Items.CHORUS_FRUIT) {
                continue;
            }
            // Skip golden apples unless allowed
            if (!config.allowGoldenApples
                    && (stack.getItem() == Items.GOLDEN_APPLE
                        || stack.getItem() == Items.ENCHANTED_GOLDEN_APPLE)) {
                continue;
            }
            // Check if player can actually eat this food
            if (foodLevel >= 20 && !food.canAlwaysEat()) {
                continue;
            }

            float score;
            switch (config.foodPriority) {
                case SATURATION:
                    score = food.saturation();
                    break;
                case NUTRITION:
                    score = food.nutrition();
                    break;
                case ANY:
                    return i;
                default:
                    score = food.saturation();
                    break;
            }
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private void resetState() {
        state = State.IDLE;
        ticksInState = 0;
        previousSlot = -1;
        foodSlot = -1;
        pausePathing = false;
        wasUsingItem = false;
    }

    @Override
    public void onLostControl() {
        baritone.getInputOverrideHandler().clearAllKeys();
        if (state != State.IDLE && previousSlot >= 0 && previousSlot <= 8) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }
        resetState();
        warned = false;
    }

    @Override
    public String displayName0() {
        return "Auto Eater";
    }

    @Override
    public boolean isTemporary() {
        return true;
    }

    @Override
    public double priority() {
        return 5;
    }
}
