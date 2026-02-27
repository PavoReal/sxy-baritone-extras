package sxy.baritoneextras.autoeater;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AutoEaterConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras.properties");
    private static final String PREFIX = "autoeater.";

    public boolean enabled = true;
    public int hungerThreshold = 20;
    public FoodPriority foodPriority = FoodPriority.SATURATION;
    public boolean allowGoldenApples = false;
    public boolean eatWhileWalking = true;

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save();
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            props.load(in);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        enabled = Boolean.parseBoolean(props.getProperty(PREFIX + "enabled", "true"));
        hungerThreshold = parseInt(props.getProperty(PREFIX + "hungerThreshold"), 20);
        allowGoldenApples = Boolean.parseBoolean(props.getProperty(PREFIX + "allowGoldenApples", "false"));
        eatWhileWalking = Boolean.parseBoolean(props.getProperty(PREFIX + "eatWhileWalking", "true"));
        try {
            foodPriority = FoodPriority.valueOf(
                    props.getProperty(PREFIX + "foodPriority", "SATURATION").toUpperCase());
        } catch (IllegalArgumentException e) {
            foodPriority = FoodPriority.SATURATION;
        }
    }

    public void save() {
        Properties props = new Properties();
        // Merge-on-save: load existing properties first to avoid clobbering other modules' keys
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        props.setProperty(PREFIX + "enabled", String.valueOf(enabled));
        props.setProperty(PREFIX + "hungerThreshold", String.valueOf(hungerThreshold));
        props.setProperty(PREFIX + "foodPriority", foodPriority.name());
        props.setProperty(PREFIX + "allowGoldenApples", String.valueOf(allowGoldenApples));
        props.setProperty(PREFIX + "eatWhileWalking", String.valueOf(eatWhileWalking));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "SXY Baritone Extras Configuration");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
