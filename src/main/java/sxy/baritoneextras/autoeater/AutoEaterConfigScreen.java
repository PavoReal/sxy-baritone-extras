package sxy.baritoneextras.autoeater;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public final class AutoEaterConfigScreen {

    private AutoEaterConfigScreen() {}

    public static ConfigCategory createCategory(AutoEaterConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("Auto Eater"))
                .tooltip(Component.literal("Automatic food eating settings"))

                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Enabled"))
                        .description(OptionDescription.of(
                                Component.literal("Enable automatic food eating during pathing")))
                        .binding(true, () -> config.enabled, val -> config.enabled = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                .option(Option.<Integer>createBuilder()
                        .name(Component.literal("Hunger Threshold"))
                        .description(OptionDescription.of(
                                Component.literal("Start eating when hunger is below this level (20 = eat whenever not full)")))
                        .binding(20, () -> config.hungerThreshold,
                                val -> config.hungerThreshold = val)
                        .controller(opt -> IntegerSliderControllerBuilder.create(opt)
                                .range(1, 20)
                                .step(1))
                        .build())

                .option(Option.<FoodPriority>createBuilder()
                        .name(Component.literal("Food Priority"))
                        .description(OptionDescription.of(
                                Component.literal("How to choose which food to eat first")))
                        .binding(FoodPriority.SATURATION,
                                () -> config.foodPriority,
                                val -> config.foodPriority = val)
                        .controller(opt -> EnumControllerBuilder.create(opt)
                                .enumClass(FoodPriority.class)
                                .formatValue(v -> Component.literal(
                                        v.name().toLowerCase(Locale.ROOT))))
                        .build())

                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Allow Golden Apples"))
                        .description(OptionDescription.of(
                                Component.literal("Allow eating golden and enchanted golden apples")))
                        .binding(false, () -> config.allowGoldenApples,
                                val -> config.allowGoldenApples = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Eat While Walking"))
                        .description(OptionDescription.of(
                                Component.literal("Eat while walking on flat terrain instead of pausing")))
                        .binding(true, () -> config.eatWhileWalking,
                                val -> config.eatWhileWalking = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                .build();
    }
}
