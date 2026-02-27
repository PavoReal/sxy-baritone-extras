package sxy.baritoneextras;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sxy.baritoneextras.autoeater.AutoEaterCommand;
import sxy.baritoneextras.autoeater.AutoEaterConfig;
import sxy.baritoneextras.autoeater.AutoEaterProcess;
import sxy.baritoneextras.mobavoidance.MobAvoidanceCommand;
import sxy.baritoneextras.mobavoidance.MobAvoidanceConfig;
import sxy.baritoneextras.mobavoidance.MobAvoidanceProcess;
import sxy.baritoneextras.torchplacer.TorchPlacerCommand;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;
import sxy.baritoneextras.torchplacer.TorchPlacerProcess;

public class BaritoneExtras implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("sxy-baritone-extras");

    private static GeneralConfig generalConfig;
    private static TorchPlacerConfig torchPlacerConfig;
    private static AutoEaterConfig autoEaterConfig;
    private static MobAvoidanceConfig mobAvoidanceConfig;

    public static GeneralConfig getGeneralConfig() {
        return generalConfig;
    }

    public static TorchPlacerConfig getConfig() {
        return torchPlacerConfig;
    }

    public static AutoEaterConfig getAutoEaterConfig() {
        return autoEaterConfig;
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

        torchPlacerConfig = new TorchPlacerConfig();
        torchPlacerConfig.load();

        autoEaterConfig = new AutoEaterConfig();
        autoEaterConfig.load();

        mobAvoidanceConfig = new MobAvoidanceConfig();
        mobAvoidanceConfig.load();

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        TorchPlacerProcess torchProcess = new TorchPlacerProcess(baritone, torchPlacerConfig);
        baritone.getPathingControlManager().registerProcess(torchProcess);

        TorchPlacerCommand torchCommand = new TorchPlacerCommand(baritone, torchPlacerConfig);
        baritone.getCommandManager().getRegistry().register(torchCommand);

        AutoEaterProcess eaterProcess = new AutoEaterProcess(baritone, autoEaterConfig);
        baritone.getPathingControlManager().registerProcess(eaterProcess);

        AutoEaterCommand eaterCommand = new AutoEaterCommand(baritone, autoEaterConfig);
        baritone.getCommandManager().getRegistry().register(eaterCommand);

        MobAvoidanceProcess mobProcess = new MobAvoidanceProcess(baritone, mobAvoidanceConfig);
        baritone.getPathingControlManager().registerProcess(mobProcess);

        MobAvoidanceCommand mobCommand = new MobAvoidanceCommand(baritone, mobAvoidanceConfig);
        baritone.getCommandManager().getRegistry().register(mobCommand);
    }
}
