package sxy.baritoneextras.roomlighter;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class RoomLighterCommand extends Command {

    private final RoomLighterConfig config;
    private final RoomLighterProcess process;

    public RoomLighterCommand(IBaritone baritone, RoomLighterConfig config, RoomLighterProcess process) {
        super(baritone, "lightroom");
        this.config = config;
        this.process = process;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        if (!args.hasAny()) {
            process.start(false);
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "start": {
                process.start(false);
                break;
            }
            case "stop": {
                process.stop();
                break;
            }
            case "scan": {
                process.start(true);
                break;
            }
            case "threshold": {
                if (!args.hasAny()) {
                    logDirect("Current light level threshold: " + config.lightLevelThreshold);
                    return;
                }
                config.lightLevelThreshold = args.getAs(Integer.class);
                config.save();
                logDirect("Room lighter threshold set to: " + config.lightLevelThreshold);
                break;
            }
            case "radius": {
                if (!args.hasAny()) {
                    logDirect("Current max scan radius: " + config.maxRadius);
                    return;
                }
                config.maxRadius = args.getAs(Integer.class);
                config.save();
                logDirect("Room lighter max radius set to: " + config.maxRadius);
                break;
            }
            case "status": {
                logDirect("Room Lighter Status:");
                logDirect("  State: " + process.getState());
                logDirect("  Threshold: " + config.lightLevelThreshold);
                logDirect("  Max radius: " + config.maxRadius);
                logDirect("  Max volume: " + config.maxVolume);
                if (process.getTotalPlanned() > 0) {
                    logDirect("  Progress: " + process.getSuccessful() + "/"
                            + process.getTotalPlanned() + " placed, "
                            + process.getRemaining() + " remaining");
                }
                break;
            }
            default:
                logDirect("Unknown sub-command: " + sub);
                logDirect("Usage: #lightroom [start|stop|scan|threshold|radius|status]");
                break;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("start", "stop", "scan", "threshold", "radius", "status")
                    .filter(s -> s.startsWith(args.peekString().toLowerCase(Locale.ROOT)));
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Scan and light up a room with torches";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Scans the room around you and places torches optimally.",
                "",
                "Usage:",
                "> lightroom - start room lighting",
                "> lightroom start - start room lighting",
                "> lightroom stop - cancel current operation",
                "> lightroom scan - dry run (scan and report only)",
                "> lightroom threshold <n> - set light level threshold",
                "> lightroom radius <n> - set max scan radius",
                "> lightroom status - show state and settings"
        );
    }
}
