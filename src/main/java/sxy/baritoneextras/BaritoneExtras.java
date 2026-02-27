package sxy.baritoneextras;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.api.ClientModInitializer;
import sxy.baritoneextras.autoeater.AutoEaterCommand;
import sxy.baritoneextras.autoeater.AutoEaterConfig;
import sxy.baritoneextras.autoeater.AutoEaterProcess;
import sxy.baritoneextras.torchplacer.TorchPlacerCommand;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;
import sxy.baritoneextras.torchplacer.TorchPlacerProcess;

public class BaritoneExtras implements ClientModInitializer {

    private static TorchPlacerConfig torchPlacerConfig;
    private static AutoEaterConfig autoEaterConfig;

    public static TorchPlacerConfig getConfig() {
        return torchPlacerConfig;
    }

    public static AutoEaterConfig getAutoEaterConfig() {
        return autoEaterConfig;
    }

    @Override
    public void onInitializeClient() {
        torchPlacerConfig = new TorchPlacerConfig();
        torchPlacerConfig.load();

        autoEaterConfig = new AutoEaterConfig();
        autoEaterConfig.load();

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        TorchPlacerProcess torchProcess = new TorchPlacerProcess(baritone, torchPlacerConfig);
        baritone.getPathingControlManager().registerProcess(torchProcess);

        TorchPlacerCommand torchCommand = new TorchPlacerCommand(baritone, torchPlacerConfig);
        baritone.getCommandManager().getRegistry().register(torchCommand);

        AutoEaterProcess eaterProcess = new AutoEaterProcess(baritone, autoEaterConfig);
        baritone.getPathingControlManager().registerProcess(eaterProcess);

        AutoEaterCommand eaterCommand = new AutoEaterCommand(baritone, autoEaterConfig);
        baritone.getCommandManager().getRegistry().register(eaterCommand);
    }
}
