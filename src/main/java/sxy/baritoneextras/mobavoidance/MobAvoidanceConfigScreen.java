package sxy.baritoneextras.mobavoidance;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.network.chat.Component;

public final class MobAvoidanceConfigScreen {

    private MobAvoidanceConfigScreen() {}

    public static ConfigCategory createCategory(MobAvoidanceConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Mob Avoidance"))
                .tooltip(Component.literal("Automatic mob avoidance and combat settings"))

                // General group
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("General"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enabled"))
                                .description(OptionDescription.of(
                                        Component.literal("Enable automatic mob avoidance during pathing")))
                                .binding(false, () -> config.enabled, val -> config.enabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Scan Radius"))
                                .description(OptionDescription.of(
                                        Component.literal("How far to scan for hostile mobs (blocks)")))
                                .binding(24, () -> config.scanRadius, val -> config.scanRadius = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(8, 48)
                                        .step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Scan Interval"))
                                .description(OptionDescription.of(
                                        Component.literal("Ticks between threat scans (higher = better performance)")))
                                .binding(4, () -> config.scanIntervalTicks, val -> config.scanIntervalTicks = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 20)
                                        .step(1))
                                .build())

                        .build())

                // Flee settings group
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Flee Settings"))

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Safe Distance"))
                                .description(OptionDescription.of(
                                        Component.literal("How far to flee before feeling safe (blocks)")))
                                .binding(16, () -> config.safeDistance, val -> config.safeDistance = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(8, 48)
                                        .step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Retreat Health Threshold"))
                                .description(OptionDescription.of(
                                        Component.literal("HP below which to always flee (out of 20)")))
                                .binding(8, () -> config.retreatHealthThreshold,
                                        val -> config.retreatHealthThreshold = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 20)
                                        .step(1))
                                .build())

                        .build())

                // Combat settings group
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Combat Settings"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Combat Enabled"))
                                .description(OptionDescription.of(
                                        Component.literal("Allow fighting mobs (vs flee-only mode)")))
                                .binding(true, () -> config.engageEnabled, val -> config.engageEnabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Max Engage Mobs"))
                                .description(OptionDescription.of(
                                        Component.literal("Maximum mobs to fight at once before fleeing")))
                                .binding(2, () -> config.engageMaxMobs, val -> config.engageMaxMobs = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 5)
                                        .step(1))
                                .build())

                        .build())

                // Mob-specific group
                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Mob-Specific"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Creeper Flee"))
                                .description(OptionDescription.of(
                                        Component.literal("Automatically flee from creepers")))
                                .binding(true, () -> config.creeperFleeEnabled,
                                        val -> config.creeperFleeEnabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Skeleton Cover"))
                                .description(OptionDescription.of(
                                        Component.literal("Seek cover from skeletons/pillagers instead of fleeing")))
                                .binding(true, () -> config.skeletonCoverEnabled,
                                        val -> config.skeletonCoverEnabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enderman Ignore"))
                                .description(OptionDescription.of(
                                        Component.literal("Ignore unprovoked endermen")))
                                .binding(true, () -> config.endermanIgnoreEnabled,
                                        val -> config.endermanIgnoreEnabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .build())

                .build();
    }
}
