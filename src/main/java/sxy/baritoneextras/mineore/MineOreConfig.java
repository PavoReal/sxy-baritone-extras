package sxy.baritoneextras.mineore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;

public final class MineOreConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras.properties");
    private static final String PREFIX = "mineore.";

    public boolean enabled = false;
    public int scanRadius = 4;
    public int scanInterval = 5;

    private final Map<OreType, Boolean> oreToggles = new EnumMap<>(OreType.class);

    public MineOreConfig() {
        for (OreType type : OreType.values()) {
            oreToggles.put(type, true);
        }
    }

    public boolean isOreEnabled(OreType type) {
        return oreToggles.getOrDefault(type, true);
    }

    public void setOreEnabled(OreType type, boolean enabled) {
        oreToggles.put(type, enabled);
    }

    public Map<OreType, Boolean> getOreToggles() {
        return oreToggles;
    }

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
        enabled = Boolean.parseBoolean(props.getProperty(PREFIX + "enabled", "false"));
        scanRadius = parseInt(props.getProperty(PREFIX + "scanRadius"), 4);
        scanInterval = parseInt(props.getProperty(PREFIX + "scanInterval"), 5);

        for (OreType type : OreType.values()) {
            String key = PREFIX + "ore." + type.configKey;
            oreToggles.put(type, Boolean.parseBoolean(props.getProperty(key, "true")));
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
        props.setProperty(PREFIX + "scanRadius", String.valueOf(scanRadius));
        props.setProperty(PREFIX + "scanInterval", String.valueOf(scanInterval));

        for (OreType type : OreType.values()) {
            String key = PREFIX + "ore." + type.configKey;
            props.setProperty(key, String.valueOf(oreToggles.getOrDefault(type, true)));
        }

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
