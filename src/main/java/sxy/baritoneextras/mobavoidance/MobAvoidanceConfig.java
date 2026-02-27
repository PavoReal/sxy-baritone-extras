package sxy.baritoneextras.mobavoidance;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class MobAvoidanceConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras-mobavoidance.properties");

    public boolean enabled = false;
    public int scanRadius = 24;
    public int scanIntervalTicks = 4;
    public int safeDistance = 16;
    public int retreatHealthThreshold = 8;
    public int engageMaxMobs = 2;
    public boolean engageEnabled = true;
    public boolean creeperFleeEnabled = true;
    public boolean skeletonCoverEnabled = true;
    public boolean endermanIgnoreEnabled = true;

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
        enabled = Boolean.parseBoolean(props.getProperty("enabled", "false"));
        scanRadius = parseInt(props.getProperty("scanRadius"), 24);
        scanIntervalTicks = parseInt(props.getProperty("scanIntervalTicks"), 4);
        safeDistance = parseInt(props.getProperty("safeDistance"), 16);
        retreatHealthThreshold = parseInt(props.getProperty("retreatHealthThreshold"), 8);
        engageMaxMobs = parseInt(props.getProperty("engageMaxMobs"), 2);
        engageEnabled = Boolean.parseBoolean(props.getProperty("engageEnabled", "true"));
        creeperFleeEnabled = Boolean.parseBoolean(props.getProperty("creeperFleeEnabled", "true"));
        skeletonCoverEnabled = Boolean.parseBoolean(props.getProperty("skeletonCoverEnabled", "true"));
        endermanIgnoreEnabled = Boolean.parseBoolean(props.getProperty("endermanIgnoreEnabled", "true"));
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("enabled", String.valueOf(enabled));
        props.setProperty("scanRadius", String.valueOf(scanRadius));
        props.setProperty("scanIntervalTicks", String.valueOf(scanIntervalTicks));
        props.setProperty("safeDistance", String.valueOf(safeDistance));
        props.setProperty("retreatHealthThreshold", String.valueOf(retreatHealthThreshold));
        props.setProperty("engageMaxMobs", String.valueOf(engageMaxMobs));
        props.setProperty("engageEnabled", String.valueOf(engageEnabled));
        props.setProperty("creeperFleeEnabled", String.valueOf(creeperFleeEnabled));
        props.setProperty("skeletonCoverEnabled", String.valueOf(skeletonCoverEnabled));
        props.setProperty("endermanIgnoreEnabled", String.valueOf(endermanIgnoreEnabled));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "SXY Baritone Extras - Mob Avoidance Config");
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
