package sxy.baritoneextras.torchplacer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class TorchPlacerConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras.properties");

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
        enabled = Boolean.parseBoolean(props.getProperty("enabled", "true"));
        lightLevelThreshold = parseInt(props.getProperty("lightLevelThreshold"), 4);
        minSpacing = parseInt(props.getProperty("minSpacing"), 8);
        safetyMargin = parseInt(props.getProperty("safetyMargin"), 2);
        try {
            placementSide = TorchPlacementSide.valueOf(
                    props.getProperty("placementSide", "RIGHT").toUpperCase());
        } catch (IllegalArgumentException e) {
            placementSide = TorchPlacementSide.RIGHT;
        }
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("enabled", String.valueOf(enabled));
        props.setProperty("lightLevelThreshold", String.valueOf(lightLevelThreshold));
        props.setProperty("placementSide", placementSide.name());
        props.setProperty("minSpacing", String.valueOf(minSpacing));
        props.setProperty("safetyMargin", String.valueOf(safetyMargin));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "SXY Baritone Extras - Torch Placer Config");
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
