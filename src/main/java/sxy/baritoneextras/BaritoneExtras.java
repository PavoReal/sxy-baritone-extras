package sxy.baritoneextras;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sxy.baritoneextras.mobavoidance.MobAvoidanceCommand;
import sxy.baritoneextras.mobavoidance.MobAvoidanceConfig;
import sxy.baritoneextras.mobavoidance.MobAvoidanceProcess;
import sxy.baritoneextras.torchplacer.TorchPlacerCommand;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;
import sxy.baritoneextras.torchplacer.TorchPlacerProcess;

public class BaritoneExtras implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("sxy-baritone-extras");

    private static GeneralConfig generalConfig;
    private static TorchPlacerConfig config;
    private static MobAvoidanceConfig mobAvoidanceConfig;

    public static GeneralConfig getGeneralConfig() {
        return generalConfig;
    }

    public static TorchPlacerConfig getConfig() {
        return config;
    }

    public static MobAvoidanceConfig getMobAvoidanceConfig() {
        return mobAvoidanceConfig;
    }

    public static void debugLog(String msg) {
        if (generalConfig != null && generalConfig.isDebugEnabled()) {
            LOGGER.info("[DEBUG] {}", msg);
        }
    }

    @Override
    public void onInitializeClient() {
        generalConfig = new GeneralConfig();
        generalConfig.load();

        config = new TorchPlacerConfig();
        config.load();

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        TorchPlacerProcess process = new TorchPlacerProcess(baritone, config);
        baritone.getPathingControlManager().registerProcess(process);

        TorchPlacerCommand command = new TorchPlacerCommand(baritone, config);
        baritone.getCommandManager().getRegistry().register(command);

        mobAvoidanceConfig = new MobAvoidanceConfig();
        mobAvoidanceConfig.load();

        MobAvoidanceProcess mobProcess = new MobAvoidanceProcess(baritone, mobAvoidanceConfig);
        baritone.getPathingControlManager().registerProcess(mobProcess);

        MobAvoidanceCommand mobCommand = new MobAvoidanceCommand(baritone, mobAvoidanceConfig);
        baritone.getCommandManager().getRegistry().register(mobCommand);
    }
}
