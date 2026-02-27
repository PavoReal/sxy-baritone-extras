package sxy.baritoneextras;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import net.fabricmc.api.ClientModInitializer;
import sxy.baritoneextras.roomlighter.RoomLighterCommand;
import sxy.baritoneextras.roomlighter.RoomLighterConfig;
import sxy.baritoneextras.roomlighter.RoomLighterProcess;
import sxy.baritoneextras.torchplacer.TorchPlacerCommand;
import sxy.baritoneextras.torchplacer.TorchPlacerConfig;
import sxy.baritoneextras.torchplacer.TorchPlacerProcess;

public class BaritoneExtras implements ClientModInitializer {

    private static TorchPlacerConfig config;
    private static RoomLighterConfig roomLighterConfig;

    public static TorchPlacerConfig getConfig() {
        return config;
    }

    public static RoomLighterConfig getRoomLighterConfig() {
        return roomLighterConfig;
    }

    @Override
    public void onInitializeClient() {
        config = new TorchPlacerConfig();
        config.load();

        roomLighterConfig = new RoomLighterConfig();
        roomLighterConfig.load();

        IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

        TorchPlacerProcess torchProcess = new TorchPlacerProcess(baritone, config);
        baritone.getPathingControlManager().registerProcess(torchProcess);

        RoomLighterProcess roomProcess = new RoomLighterProcess(baritone, roomLighterConfig);
        baritone.getPathingControlManager().registerProcess(roomProcess);

        TorchPlacerCommand torchCommand = new TorchPlacerCommand(baritone, config);
        baritone.getCommandManager().getRegistry().register(torchCommand);

        RoomLighterCommand roomCommand = new RoomLighterCommand(baritone, roomLighterConfig, roomProcess);
        baritone.getCommandManager().getRegistry().register(roomCommand);
    }
}
