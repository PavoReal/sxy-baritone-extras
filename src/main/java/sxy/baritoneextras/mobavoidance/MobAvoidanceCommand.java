package sxy.baritoneextras.mobavoidance;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class MobAvoidanceCommand extends Command {

    private final MobAvoidanceConfig config;

    public MobAvoidanceCommand(IBaritone baritone, MobAvoidanceConfig config) {
        super(baritone, "mobavoid");
        this.config = config;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        if (!args.hasAny()) {
            config.enabled = !config.enabled;
            config.save();
            logDirect("Mob avoidance " + (config.enabled ? "enabled" : "disabled"));
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "radius": {
                if (!args.hasAny()) {
                    logDirect("Current scan radius: " + config.scanRadius);
                    return;
                }
                config.scanRadius = args.getAs(Integer.class);
                config.save();
                logDirect("Scan radius set to: " + config.scanRadius);
                break;
            }
            case "safe": {
                if (!args.hasAny()) {
                    logDirect("Current safe distance: " + config.safeDistance);
                    return;
                }
                config.safeDistance = args.getAs(Integer.class);
                config.save();
                logDirect("Safe flee distance set to: " + config.safeDistance);
                break;
            }
            case "health": {
                if (!args.hasAny()) {
                    logDirect("Current retreat health threshold: " + config.retreatHealthThreshold);
                    return;
                }
                config.retreatHealthThreshold = args.getAs(Integer.class);
                config.save();
                logDirect("Retreat health threshold set to: " + config.retreatHealthThreshold);
                break;
            }
            case "combat": {
                if (!args.hasAny()) {
                    config.engageEnabled = !config.engageEnabled;
                } else {
                    String val = args.getString().toLowerCase(Locale.ROOT);
                    config.engageEnabled = val.equals("on") || val.equals("true");
                }
                config.save();
                logDirect("Combat " + (config.engageEnabled ? "enabled" : "disabled"));
                break;
            }
            case "maxmobs": {
                if (!args.hasAny()) {
                    logDirect("Current max engage mobs: " + config.engageMaxMobs);
                    return;
                }
                config.engageMaxMobs = args.getAs(Integer.class);
                config.save();
                logDirect("Max engage mobs set to: " + config.engageMaxMobs);
                break;
            }
            case "status": {
                logDirect("Mob Avoidance Status:");
                logDirect("  Enabled: " + config.enabled);
                logDirect("  Scan radius: " + config.scanRadius);
                logDirect("  Scan interval: " + config.scanIntervalTicks + " ticks");
                logDirect("  Safe distance: " + config.safeDistance);
                logDirect("  Retreat health: " + config.retreatHealthThreshold);
                logDirect("  Combat enabled: " + config.engageEnabled);
                logDirect("  Max engage mobs: " + config.engageMaxMobs);
                logDirect("  Creeper flee: " + config.creeperFleeEnabled);
                logDirect("  Skeleton cover: " + config.skeletonCoverEnabled);
                logDirect("  Enderman ignore: " + config.endermanIgnoreEnabled);
                break;
            }
            default:
                logDirect("Unknown sub-command: " + sub);
                logDirect("Usage: #mobavoid [radius|safe|health|combat|maxmobs|status]");
                break;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("radius", "safe", "health", "combat", "maxmobs", "status")
                    .filter(s -> s.startsWith(args.peekString().toLowerCase(Locale.ROOT)));
        }
        if (args.hasExactly(2)) {
            String sub = args.peekString().toLowerCase(Locale.ROOT);
            if ("combat".equals(sub)) {
                args.get(); // consume "combat"
                String partial = args.peekString().toLowerCase(Locale.ROOT);
                return Stream.of("on", "off")
                        .filter(s -> s.startsWith(partial));
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Control mob avoidance and combat";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Controls automatic mob avoidance during pathing.",
                "",
                "Usage:",
                "> mobavoid - toggle on/off",
                "> mobavoid radius <n> - set scan radius",
                "> mobavoid safe <n> - set safe flee distance",
                "> mobavoid health <n> - set retreat health threshold",
                "> mobavoid combat [on|off] - toggle combat",
                "> mobavoid maxmobs <n> - max mobs to engage at once",
                "> mobavoid status - show current settings"
        );
    }
}
