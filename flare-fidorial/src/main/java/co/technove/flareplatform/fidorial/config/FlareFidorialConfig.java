package co.technove.flareplatform.fidorial.config;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class FlareFidorialConfig {

    private static final String FILE_NAME = "flare.properties";

    private final Path file;
    private final Logger logger;

    private String token = "";
    private URI backendUrl = URI.create("https://flare.airplane.gg");
    private String viewerUrl = "";
    private boolean commandConsoleOnly = true;

    public FlareFidorialConfig(Path dataFolder, Logger logger) {
        this.file = dataFolder.resolve(FILE_NAME);
        this.logger = logger;
    }

    public void load() {
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException e) {
                logger.error("Could not read {}, using defaults", file, e);
            }
        }

        this.token = props.getProperty("token", token).strip();
        this.viewerUrl = props.getProperty("viewer-url", viewerUrl).strip();
        this.commandConsoleOnly = Boolean.parseBoolean(
            props.getProperty("command-console-only", Boolean.toString(commandConsoleOnly)).strip());
        String rawBackend = props.getProperty("backend-url", backendUrl.toString()).strip();
        try {
            this.backendUrl = URI.create(rawBackend);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid backend-url '{}', keeping {}", rawBackend, backendUrl);
        }

        if (!Files.isRegularFile(file)) {
            write();
        }
    }

    private void write() {
        Properties props = new Properties();
        props.setProperty("token", token);
        props.setProperty("backend-url", backendUrl.toString());
        props.setProperty("viewer-url", viewerUrl);
        props.setProperty("command-console-only", Boolean.toString(commandConsoleOnly));
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Flare configuration. token/backend-url are required to upload profiles."
                    + " Fidorial has no permission system yet: command-console-only restricts /flare to the console.");
            }
        } catch (IOException e) {
            logger.error("Could not write {}", file, e);
        }
    }

    public String token() {
        return token;
    }

    public URI backendUrl() {
        return backendUrl;
    }

    public String viewerUrl() {
        return viewerUrl;
    }

    public boolean commandConsoleOnly() {
        return commandConsoleOnly;
    }
}
