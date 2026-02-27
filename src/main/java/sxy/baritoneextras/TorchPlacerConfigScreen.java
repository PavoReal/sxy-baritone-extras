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
import sxy.baritoneextras.torchplacer.TorchPlacementSide;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;

import java.util.Locale;

public final class TorchPlacerConfigScreen {

    private TorchPlacerConfigScreen() {}

    public static Screen create(Screen parent) {
        TorchPlacerConfig config = BaritoneExtras.getConfig();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Torch Placer Configuration"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("General"))
                        .tooltip(Component.literal("Automatic torch placement settings"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enabled"))
                                .description(OptionDescription.of(
                                        Component.literal("Enable automatic torch placement during pathing")))
                                .binding(true, () -> config.enabled, val -> config.enabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())

                        .option(Option.<TorchPlacementSide>createBuilder()
                                .name(Component.literal("Placement Side"))
                                .description(OptionDescription.of(
                                        Component.literal("Where to place torches relative to the path direction")))
                                .binding(TorchPlacementSide.RIGHT,
                                        () -> config.placementSide,
                                        val -> config.placementSide = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(TorchPlacementSide.class)
                                        .formatValue(v -> Component.literal(
                                                v.name().toLowerCase(Locale.ROOT))))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Light Level Threshold"))
                                .description(OptionDescription.of(
                                        Component.literal("Place torches when light level is at or below this value")))
                                .binding(4, () -> config.lightLevelThreshold,
                                        val -> config.lightLevelThreshold = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(0, 15)
                                        .step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Minimum Spacing"))
                                .description(OptionDescription.of(
                                        Component.literal("Minimum number of blocks between placed torches")))
                                .binding(8, () -> config.minSpacing,
                                        val -> config.minSpacing = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(1, 20)
                                        .step(1))
                                .build())

                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Safety Margin"))
                                .description(OptionDescription.of(
                                        Component.literal("Extra blocks of margin when deciding torch placement")))
                                .binding(2, () -> config.safetyMargin,
                                        val -> config.safetyMargin = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                        .range(0, 10)
                                        .step(1))
                                .build())

                        .build())
                .save(config::save)
                .build()
                .generateScreen(parent);
    }
}
