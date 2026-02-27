package sxy.baritoneextras.autoeater;

import baritone.api.IBaritone;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class AutoEaterCommand extends Command {

    private final AutoEaterConfig config;

    public AutoEaterCommand(IBaritone baritone, AutoEaterConfig config) {
        super(baritone, "autoeater");
        this.config = config;
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        if (!args.hasAny()) {
            config.enabled = !config.enabled;
            config.save();
            logDirect("Auto eater " + (config.enabled ? "enabled" : "disabled"));
            return;
        }

        String sub = args.getString().toLowerCase(Locale.ROOT);
        switch (sub) {
            case "threshold": {
                if (!args.hasAny()) {
                    logDirect("Current hunger threshold: " + config.hungerThreshold);
                    return;
                }
                int value = args.getAs(Integer.class);
                if (value < 1 || value > 20) {
                    logDirect("Threshold must be between 1 and 20");
                    return;
                }
                config.hungerThreshold = value;
                config.save();
                logDirect("Hunger threshold set to: " + config.hungerThreshold);
                break;
            }
            case "priority": {
                if (!args.hasAny()) {
                    logDirect("Current food priority: " + config.foodPriority.name().toLowerCase(Locale.ROOT));
                    return;
                }
                String value = args.getString().toUpperCase(Locale.ROOT);
                try {
                    config.foodPriority = FoodPriority.valueOf(value);
                } catch (IllegalArgumentException e) {
                    logDirect("Invalid priority. Use: saturation, nutrition, any");
                    return;
                }
                config.save();
                logDirect("Food priority set to: " + config.foodPriority.name().toLowerCase(Locale.ROOT));
                break;
            }
            case "goldenapples": {
                config.allowGoldenApples = !config.allowGoldenApples;
                config.save();
                logDirect("Golden apples " + (config.allowGoldenApples ? "allowed" : "excluded"));
                break;
            }
            case "walking": {
                config.eatWhileWalking = !config.eatWhileWalking;
                config.save();
                logDirect("Eat while walking " + (config.eatWhileWalking ? "enabled" : "disabled"));
                break;
            }
            case "status": {
                logDirect("Auto Eater Status:");
                logDirect("  Enabled: " + config.enabled);
                logDirect("  Hunger threshold: " + config.hungerThreshold);
                logDirect("  Food priority: " + config.foodPriority.name().toLowerCase(Locale.ROOT));
                logDirect("  Golden apples: " + (config.allowGoldenApples ? "allowed" : "excluded"));
                logDirect("  Eat while walking: " + config.eatWhileWalking);
                break;
            }
            default:
                logDirect("Unknown sub-command: " + sub);
                logDirect("Usage: #autoeater [threshold|priority|goldenapples|walking|status]");
                break;
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return Stream.of("threshold", "priority", "goldenapples", "walking", "status")
                    .filter(s -> s.startsWith(args.peekString().toLowerCase(Locale.ROOT)));
        }
        if (args.hasExactly(2)) {
            String sub = args.peekString().toLowerCase(Locale.ROOT);
            if ("priority".equals(sub)) {
                args.get(); // consume "priority"
                String partial = args.peekString().toLowerCase(Locale.ROOT);
                return Stream.of("saturation", "nutrition", "any")
                        .filter(s -> s.startsWith(partial));
            }
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Control automatic food eating";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
                "Controls automatic food eating during pathing.",
                "",
                "Usage:",
                "> autoeater - toggle on/off",
                "> autoeater threshold <1-20> - set hunger threshold to start eating",
                "> autoeater priority <saturation|nutrition|any> - set food selection priority",
                "> autoeater goldenapples - toggle golden apple usage",
                "> autoeater walking - toggle eating while walking",
                "> autoeater status - show current settings"
        );
    }
}
