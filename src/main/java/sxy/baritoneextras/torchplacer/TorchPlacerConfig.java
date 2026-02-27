package sxy.baritoneextras.torchplacer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TorchPlacerConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras.properties");
    private static final String PREFIX = "torchplacer.";

    public boolean enabled = true;
    public int lightLevelThreshold = 4;
    public TorchPlacementSide placementSide = TorchPlacementSide.RIGHT;
    public int minSpacing = 8;
    public int safetyMargin = 2;

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
        enabled = Boolean.parseBoolean(
                getWithFallback(props, "enabled", "true"));
        lightLevelThreshold = parseInt(
                getWithFallback(props, "lightLevelThreshold", null), 4);
        minSpacing = parseInt(
                getWithFallback(props, "minSpacing", null), 8);
        safetyMargin = parseInt(
                getWithFallback(props, "safetyMargin", null), 2);
        try {
            placementSide = TorchPlacementSide.valueOf(
                    getWithFallback(props, "placementSide", "RIGHT").toUpperCase());
        } catch (IllegalArgumentException e) {
            placementSide = TorchPlacementSide.RIGHT;
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
        // Remove old un-prefixed keys for backward compat migration
        props.remove("enabled");
        props.remove("lightLevelThreshold");
        props.remove("placementSide");
        props.remove("minSpacing");
        props.remove("safetyMargin");
        // Write prefixed keys
        props.setProperty(PREFIX + "enabled", String.valueOf(enabled));
        props.setProperty(PREFIX + "lightLevelThreshold", String.valueOf(lightLevelThreshold));
        props.setProperty(PREFIX + "placementSide", placementSide.name());
        props.setProperty(PREFIX + "minSpacing", String.valueOf(minSpacing));
        props.setProperty(PREFIX + "safetyMargin", String.valueOf(safetyMargin));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "SXY Baritone Extras Config");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getWithFallback(Properties props, String key, String defaultValue) {
        String prefixed = props.getProperty(PREFIX + key);
        if (prefixed != null) {
            return prefixed;
        }
        return props.getProperty(key, defaultValue);
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
