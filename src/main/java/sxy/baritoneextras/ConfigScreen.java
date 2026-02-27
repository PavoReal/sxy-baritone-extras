package sxy.baritoneextras;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import sxy.baritoneextras.autoeater.AutoEaterConfig;
import sxy.baritoneextras.autoeater.FoodPriority;
import sxy.baritoneextras.torchplacer.TorchPlacementSide;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;

import java.util.Locale;

public final class ConfigScreen {

    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        TorchPlacerConfig torchConfig = BaritoneExtras.getConfig();
        AutoEaterConfig eaterConfig = BaritoneExtras.getAutoEaterConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("SXY Baritone Extras Configuration"))

                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Torch Placer"))
                        .tooltip(Component.literal("Automatic torch placement settings"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enabled"))
                                .description(OptionDescription.of(
                                        Component.literal("Enable automatic torch placement during pathing")))
                                .binding(true, () -> torchConfig.enabled, val -> torchConfig.enabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<TorchPlacementSide>createBuilder()
                                .name(Component.literal("Placement Side"))
                                .description(OptionDescription.of(
                                        Component.literal("Where to place torches relative to the path direction")))
                                .binding(TorchPlacementSide.RIGHT,
                                        () -> torchConfig.placementSide,
                                        val -> torchConfig.placementSide = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(TorchPlacementSide.class)
                                        .formatValue(v -> Component.literal(
                                                v.name().toLowerCase(Locale.ROOT))))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Light Level Threshold"))
                                .description(OptionDescription.of(
                                        Component.literal("Place torches when light level is at or below this value")))
                                .binding(4, () -> torchConfig.lightLevelThreshold,
                                        val -> torchConfig.lightLevelThreshold = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(0, 15)
                                        .step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Minimum Spacing"))
                                .description(OptionDescription.of(
                                        Component.literal("Minimum number of blocks between placed torches")))
                                .binding(8, () -> torchConfig.minSpacing,
                                        val -> torchConfig.minSpacing = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 20)
                                        .step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Safety Margin"))
                                .description(OptionDescription.of(
                                        Component.literal("Extra blocks of margin when deciding torch placement")))
                                .binding(2, () -> torchConfig.safetyMargin,
                                        val -> torchConfig.safetyMargin = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(0, 10)
                                        .step(1))
                                .build())

                        .build())

                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Auto Eater"))
                        .tooltip(Component.literal("Automatic food eating settings"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enabled"))
                                .description(OptionDescription.of(
                                        Component.literal("Enable automatic food eating during pathing")))
                                .binding(true, () -> eaterConfig.enabled, val -> eaterConfig.enabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Hunger Threshold"))
                                .description(OptionDescription.of(
                                        Component.literal("Start eating when hunger is below this level (20 = eat whenever not full)")))
                                .binding(20, () -> eaterConfig.hungerThreshold,
                                        val -> eaterConfig.hungerThreshold = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 20)
                                        .step(1))
                                .build())

                        .option(Option.<FoodPriority>createBuilder()
                                .name(Component.literal("Food Priority"))
                                .description(OptionDescription.of(
                                        Component.literal("How to choose which food to eat first")))
                                .binding(FoodPriority.SATURATION,
                                        () -> eaterConfig.foodPriority,
                                        val -> eaterConfig.foodPriority = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(FoodPriority.class)
                                        .formatValue(v -> Component.literal(
                                                v.name().toLowerCase(Locale.ROOT))))
                                .build())

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Allow Golden Apples"))
                                .description(OptionDescription.of(
                                        Component.literal("Allow eating golden and enchanted golden apples")))
                                .binding(false, () -> eaterConfig.allowGoldenApples,
                                        val -> eaterConfig.allowGoldenApples = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Eat While Walking"))
                                .description(OptionDescription.of(
                                        Component.literal("Eat while walking on flat terrain instead of pausing")))
                                .binding(true, () -> eaterConfig.eatWhileWalking,
                                        val -> eaterConfig.eatWhileWalking = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .build())

                .save(() -> {
                    torchConfig.save();
                    eaterConfig.save();
                })
                .build()
                .generateScreen(parent);
    }
}
