package sxy.baritoneextras;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import sxy.baritoneextras.autoeater.AutoEaterConfig;
import sxy.baritoneextras.autoeater.AutoEaterConfigScreen;
import sxy.baritoneextras.mobavoidance.MobAvoidanceConfig;
import sxy.baritoneextras.mobavoidance.MobAvoidanceConfigScreen;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;

public class ModMenuIntegration implements ModMenuApi {

    private static ConfigCategory createGeneralCategory(GeneralConfig config) {
        return ConfigCategory.createBuilder()
                .name(Component.literal("General"))
                .tooltip(Component.literal("General mod settings"))

                .option(Option.<Boolean>createBuilder()
                        .name(Component.literal("Debug Logging"))
                        .description(OptionDescription.of(
                                Component.literal("Log debug messages to the game console/terminal. "
                                        + "Useful for troubleshooting torch placement and mob avoidance behavior.")))
                        .binding(true, () -> config.debugEnabled, val -> config.debugEnabled = val)
                        .controller(TickBoxControllerBuilder::create)
                        .build())

                .group(OptionGroup.createBuilder()
                        .name(Component.literal("Build Info"))
                        .description(OptionDescription.of(
                                Component.literal("Build metadata for debugging")))
                        .option(LabelOption.create(
                                Component.literal("Git Branch: " + BuildInfo.GIT_BRANCH)))
                        .option(LabelOption.create(
                                Component.literal("Build Time: " + BuildInfo.BUILD_TIME)))
                        .build())

                .build();
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            if (!FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3")) {
                return null;
            }

            GeneralConfig generalConfig = BaritoneExtras.getGeneralConfig();
            TorchPlacerConfig torchConfig = BaritoneExtras.getConfig();
            AutoEaterConfig eaterConfig = BaritoneExtras.getAutoEaterConfig();
            MobAvoidanceConfig mobConfig = BaritoneExtras.getMobAvoidanceConfig();

            return YetAnotherConfigLib.createBuilder()
                    .title(Component.literal("SXY Baritone Extras Configuration"))
                    .category(createGeneralCategory(generalConfig))
                    .category(TorchPlacerConfigScreen.createCategory(torchConfig))
                    .category(AutoEaterConfigScreen.createCategory(eaterConfig))
                    .category(MobAvoidanceConfigScreen.createCategory(mobConfig))
                    .save(() -> {
                        generalConfig.save();
                        torchConfig.save();
                        eaterConfig.save();
                        mobConfig.save();
                    })
                    .build()
                    .generateScreen(parent);
        };
    }
}
