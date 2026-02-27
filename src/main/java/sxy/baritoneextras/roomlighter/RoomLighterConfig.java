package sxy.baritoneextras.roomlighter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class RoomLighterConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras.properties");
    private static final String PREFIX = "roomlighter.";

    public int lightLevelThreshold = 7;
    public int maxRadius = 32;
    public int maxVolume = 10000;

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
        lightLevelThreshold = parseInt(props.getProperty(PREFIX + "lightLevelThreshold"), 7);
        maxRadius = parseInt(props.getProperty(PREFIX + "maxRadius"), 32);
        maxVolume = parseInt(props.getProperty(PREFIX + "maxVolume"), 10000);
    }

    public void save() {
        Properties props = new Properties();
        // Read existing file to preserve other configs' keys
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        props.setProperty(PREFIX + "lightLevelThreshold", String.valueOf(lightLevelThreshold));
        props.setProperty(PREFIX + "maxRadius", String.valueOf(maxRadius));
        props.setProperty(PREFIX + "maxVolume", String.valueOf(maxVolume));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "SXY Baritone Extras Config");
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
