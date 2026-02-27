package sxy.baritoneextras.torchplacer;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class TorchPlacerCommand extends Command {

    private final TorchPlacerConfig config;

    public TorchPlacerCommand(IBaritone baritone, TorchPlacerConfig config) {
        super(baritone, "torchplacer");
        this.config = config;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        if (!args.hasAny()) {
            config.enabled = !config.enabled;
            config.save();
            logDirect("Torch placer " + (config.enabled ? "enabled" : "disabled"));
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "side": {
                if (!args.hasAny()) {
                    logDirect("Current placement side: " + config.placementSide.name().toLowerCase(Locale.ROOT));
                    return;
                }
                String value = args.getString().toUpperCase(Locale.ROOT);
                try {
                    config.placementSide = TorchPlacementSide.valueOf(value);
                } catch (IllegalArgumentException e) {
                    logDirect("Invalid side. Use: floor, left, right");
                    return;
                }
                config.save();
                logDirect("Torch placement side set to: " + config.placementSide.name().toLowerCase(Locale.ROOT));
                break;
            }
            case "spacing": {
                if (!args.hasAny()) {
                    logDirect("Current min spacing: " + config.minSpacing);
                    return;
                }
                config.minSpacing = args.getAs(Integer.class);
                config.save();
                logDirect("Torch min spacing set to: " + config.minSpacing);
                break;
            }
            case "threshold": {
                if (!args.hasAny()) {
                    logDirect("Current light level threshold: " + config.lightLevelThreshold);
                    return;
                }
                config.lightLevelThreshold = args.getAs(Integer.class);
                config.save();
                logDirect("Torch light level threshold set to: " + config.lightLevelThreshold);
                break;
            }
            case "margin": {
                if (!args.hasAny()) {
                    logDirect("Current safety margin: " + config.safetyMargin);
                    return;
                }
                config.safetyMargin = args.getAs(Integer.class);
                config.save();
                logDirect("Torch safety margin set to: " + config.safetyMargin);
                break;
            }
            case "status": {
                logDirect("Torch Placer Status:");
                logDirect("  Enabled: " + config.enabled);
                logDirect("  Side: " + config.placementSide.name().toLowerCase(Locale.ROOT));
                logDirect("  Light threshold: " + config.lightLevelThreshold);
                logDirect("  Min spacing: " + config.minSpacing);
                logDirect("  Safety margin: " + config.safetyMargin);
                break;
            }
            default:
                logDirect("Unknown sub-command: " + sub);
                logDirect("Usage: #torchplacer [side|spacing|threshold|margin|status]");
                break;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("side", "spacing", "threshold", "margin", "status")
                    .filter(s -> s.startsWith(args.peekString().toLowerCase(Locale.ROOT)));
        }
        if (args.hasExactly(2)) {
            String sub = args.peekString().toLowerCase(Locale.ROOT);
            if ("side".equals(sub)) {
                args.get(); // consume "side"
                String partial = args.peekString().toLowerCase(Locale.ROOT);
                return Stream.of("floor", "left", "right")
                        .filter(s -> s.startsWith(partial));
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Control automatic torch placement";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Controls automatic torch placement during pathing.",
                "",
                "Usage:",
                "> torchplacer - toggle on/off",
                "> torchplacer side <floor|left|right> - set placement side",
                "> torchplacer spacing <n> - set min spacing between torches",
                "> torchplacer threshold <n> - set light level threshold",
                "> torchplacer margin <n> - set safety margin",
                "> torchplacer status - show current settings"
        );
    }
}
