package sxy.baritoneextras;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
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
            MobAvoidanceConfig mobConfig = BaritoneExtras.getMobAvoidanceConfig();

            return YetAnotherConfigLib.createBuilder()
                    .title(Component.literal("SXY Baritone Extras Configuration"))
                    .category(createGeneralCategory(generalConfig))
                    .category(TorchPlacerConfigScreen.createCategory(torchConfig))
                    .category(MobAvoidanceConfigScreen.createCategory(mobConfig))
                    .save(() -> {
                        generalConfig.save();
                        torchConfig.save();
                        mobConfig.save();
                    })
                    .build()
                    .generateScreen(parent);
        };
    }
}
