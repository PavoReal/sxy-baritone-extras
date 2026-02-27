package sxy.baritoneextras;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class GeneralConfig {

    private static final Path CONFIG_PATH = Path.of("config", "sxy-baritone-extras-general.properties");

    public boolean debugEnabled = true;

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
        debugEnabled = Boolean.parseBoolean(props.getProperty("debugEnabled", "true"));
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty("debugEnabled", String.valueOf(debugEnabled));
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "SXY Baritone Extras - General Config");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}
