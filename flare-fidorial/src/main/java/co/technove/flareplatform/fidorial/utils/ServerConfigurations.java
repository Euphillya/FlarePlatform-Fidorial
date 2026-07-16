package co.technove.flareplatform.fidorial.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ServerConfigurations {

    private static final List<String> CONFIGURATION_FILES = List.of("fidorial.properties");
    private static final List<Pattern> HIDDEN_ENTRIES_PATTERNS = List.of(
        Pattern.compile(".*token.*"),
        Pattern.compile(".*secret.*"),
        Pattern.compile(".*password.*")
    );

    public static Map<String, String> getCleanCopies() throws IOException {
        Map<String, String> files = new HashMap<>(CONFIGURATION_FILES.size());
        for (final String file : CONFIGURATION_FILES) {
            Path path = Path.of(file);
            if (Files.exists(path)) {
                files.put(file, getCleanCopy(path));
            }
        }
        return files;
    }

    private static boolean matchesRegex(String key) {
        for (final Pattern pattern : HIDDEN_ENTRIES_PATTERNS) {
            if (pattern.matcher(key).matches()) {
                return true;
            }
        }
        return false;
    }

    private static String getCleanCopy(Path configPath) throws IOException {
        if (configPath.getFileName().toString().endsWith(".properties")) {
            final Properties properties = new Properties();
            try (final InputStream inputStream = Files.newInputStream(configPath)) {
                properties.load(inputStream);
            }
            for (final String hiddenConfig : properties.stringPropertyNames()) {
                if (matchesRegex(hiddenConfig)) {
                    properties.remove(hiddenConfig);
                }
            }
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            properties.store(outputStream, "");
            return Arrays.stream(outputStream.toString().split("\n"))
                .filter(line -> !line.startsWith("#"))
                .collect(Collectors.joining("\n"));
        }
        return Files.readString(configPath);
    }
}
