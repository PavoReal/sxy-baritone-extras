package sxy.baritoneextras;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.api.ClientModInitializer;
import sxy.baritoneextras.torchplacer.TorchPlacerCommand;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;
import sxy.baritoneextras.torchplacer.TorchPlacerProcess;

public class BaritoneExtras implements ClientModInitializer {

    private static TorchPlacerConfig config;

    public static TorchPlacerConfig getConfig() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = new TorchPlacerConfig();
        config.load();

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        TorchPlacerProcess process = new TorchPlacerProcess(baritone, config);
        baritone.getPathingControlManager().registerProcess(process);

        TorchPlacerCommand command = new TorchPlacerCommand(baritone, config);
        baritone.getCommandManager().getRegistry().register(command);
    }
}
