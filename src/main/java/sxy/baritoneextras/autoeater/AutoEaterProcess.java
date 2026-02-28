package sxy.baritoneextras.autoeater;

import baritone.api.IBaritone;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.Helper;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.input.Input;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class AutoEaterProcess implements IBaritoneProcess {

    private static final int EAT_TIMEOUT_TICKS = 40;
    private static final int MIN_EAT_TICKS = 10;
    private static final int COOLDOWN_TICKS = 30;

    private enum State { IDLE, SELECTING, EATING, FINISHING }

    private final IBaritone baritone;
    private final IPlayerContext ctx;
    private final AutoEaterConfig config;

    private State state = State.IDLE;
    private int ticksInState;
    private int previousSlot = -1;
    private int foodSlot = -1;
    private boolean wasUsingItem;
    private boolean warned;
    private int cooldownTicks;

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
        // Cooldown prevents rapid re-activation after eating
        if (cooldownTicks > 0) {
            cooldownTicks--;
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
        state = State.SELECTING;
        ticksInState = 0;
        return new PathingCommand(null, PathingCommandType.DEFER);
    }

    private PathingCommand tickSelecting() {
        previousSlot = ctx.player().getInventory().getSelectedSlot();
        ctx.player().getInventory().setSelectedSlot(foodSlot);
        state = State.EATING;
        ticksInState = 0;
        wasUsingItem = false;
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickEating(boolean isSafeToCancel) {
        ticksInState++;

        // Safety: if Baritone urgently needs control, abort eating
        if (!isSafeToCancel) {
            abortEating();
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        // Re-confirm the selected slot hasn't been changed by Baritone
        if (ctx.player().getInventory().getSelectedSlot() != foodSlot) {
            ctx.player().getInventory().setSelectedSlot(foodSlot);
        }

        // Verify held item is still food (stack may have been consumed)
        ItemStack held = ctx.player().getInventory().getItem(foodSlot);
        if (held.get(DataComponents.FOOD) == null) {
            state = State.FINISHING;
            ticksInState = 0;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        // Force right-click to eat — set early to minimize input gaps
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, true);

        // Detect eating completion: player was using item and stopped.
        // Only check after MIN_EAT_TICKS to avoid false triggers from brief
        // input interruptions (e.g. Baritone clearing overrides between ticks).
        boolean usingItem = ctx.player().isUsingItem();
        if (ticksInState > MIN_EAT_TICKS && wasUsingItem && !usingItem) {
            state = State.FINISHING;
            ticksInState = 0;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }
        wasUsingItem = usingItem;

        // Timeout safety net
        if (ticksInState >= EAT_TIMEOUT_TICKS) {
            state = State.FINISHING;
            ticksInState = 0;
            baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
            return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
        }

        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private PathingCommand tickFinishing() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        if (previousSlot >= 0 && previousSlot <= 8) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }
        cooldownTicks = COOLDOWN_TICKS;
        resetState();
        return new PathingCommand(null, PathingCommandType.REQUEST_PAUSE);
    }

    private void abortEating() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        if (previousSlot >= 0 && previousSlot <= 8) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }
        cooldownTicks = COOLDOWN_TICKS;
        resetState();
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
        wasUsingItem = false;
    }

    @Override
    public void onLostControl() {
        baritone.getInputOverrideHandler().setInputForceState(Input.CLICK_RIGHT, false);
        if (state != State.IDLE && previousSlot >= 0 && previousSlot <= 8) {
            ctx.player().getInventory().setSelectedSlot(previousSlot);
        }
        resetState();
        cooldownTicks = 0;
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
        return 6;
    }
}
