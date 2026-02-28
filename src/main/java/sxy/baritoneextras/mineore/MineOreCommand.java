package sxy.baritoneextras.mineore;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class MineOreCommand extends Command {

    private final MineOreConfig config;

    public MineOreCommand(IBaritone baritone, MineOreConfig config) {
        super(baritone, "mineore");
        this.config = config;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        if (!args.hasAny()) {
            config.enabled = !config.enabled;
            config.save();
            logDirect("Mine Ore " + (config.enabled ? "enabled" : "disabled"));
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "radius": {
                if (!args.hasAny()) {
                    logDirect("Current scan radius: " + config.scanRadius);
                    return;
                }
                int value = args.getAs(Integer.class);
                if (value < 1 || value > 8) {
                    logDirect("Radius must be between 1 and 8");
                    return;
                }
                config.scanRadius = value;
                config.save();
                logDirect("Scan radius set to: " + config.scanRadius);
                break;
            }
            case "ore": {
                if (!args.hasAny()) {
                    logDirect("Usage: #mineore ore <type> [on|off]");
                    logDirect("Types: " + getOreTypeNames());
                    return;
                }
                String typeName = args.getString().toLowerCase(Locale.ROOT);
                OreType type = findOreType(typeName);
                if (type == null) {
                    logDirect("Unknown ore type: " + typeName);
                    logDirect("Types: " + getOreTypeNames());
                    return;
                }
                if (args.hasAny()) {
                    String onOff = args.getString().toLowerCase(Locale.ROOT);
                    boolean enabled = "on".equals(onOff) || "true".equals(onOff) || "yes".equals(onOff);
                    config.setOreEnabled(type, enabled);
                } else {
                    config.setOreEnabled(type, !config.isOreEnabled(type));
                }
                config.save();
                logDirect(type.configKey + " mining " + (config.isOreEnabled(type) ? "enabled" : "disabled"));
                break;
            }
            case "ores": {
                logDirect("Ore Types:");
                for (Map.Entry<OreType, Boolean> entry : config.getOreToggles().entrySet()) {
                    logDirect("  " + entry.getKey().configKey + ": " + (entry.getValue() ? "enabled" : "disabled"));
                }
                break;
            }
            case "status": {
                logDirect("Mine Ore Status:");
                logDirect("  Enabled: " + config.enabled);
                logDirect("  Scan radius: " + config.scanRadius);
                logDirect("  Scan interval: " + config.scanInterval + " ticks");
                logDirect("  Ore types:");
                for (Map.Entry<OreType, Boolean> entry : config.getOreToggles().entrySet()) {
                    logDirect("    " + entry.getKey().configKey + ": "
                            + (entry.getValue() ? "enabled" : "disabled"));
                }
                break;
            }
            default:
                logDirect("Unknown sub-command: " + sub);
                logDirect("Usage: #mineore [radius|ore|ores|status]");
                break;
        }
    }

    private OreType findOreType(String name) {
        for (OreType type : OreType.values()) {
            if (type.configKey.equals(name)) {
                return type;
            }
        }
        return null;
    }

    private String getOreTypeNames() {
        StringBuilder sb = new StringBuilder();
        for (OreType type : OreType.values()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(type.configKey);
        }
        return sb.toString();
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("radius", "ore", "ores", "status")
                    .filter(s -> s.startsWith(args.peekString().toLowerCase(Locale.ROOT)));
        }
        if (args.hasExactly(2)) {
            String sub = args.peekString().toLowerCase(Locale.ROOT);
            if ("ore".equals(sub)) {
                args.get(); // consume "ore"
                String partial = args.peekString().toLowerCase(Locale.ROOT);
                return Arrays.stream(OreType.values())
                        .map(t -> t.configKey)
                        .filter(s -> s.startsWith(partial));
            }
        }
        if (args.hasExactly(3)) {
            String sub = args.peekString().toLowerCase(Locale.ROOT);
            if ("ore".equals(sub)) {
                args.get(); // consume "ore"
                args.get(); // consume type name
                String partial = args.peekString().toLowerCase(Locale.ROOT);
                return Stream.of("on", "off")
                        .filter(s -> s.startsWith(partial));
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Control automatic ore mining";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Controls automatic ore vein mining during pathing.",
                "",
                "Usage:",
                "> mineore - toggle on/off",
                "> mineore radius <1-8> - set scan radius",
                "> mineore ore <type> - toggle specific ore type",
                "> mineore ore <type> on|off - explicitly set ore type",
                "> mineore ores - list all ore types with status",
                "> mineore status - show all settings"
        );
    }
}
