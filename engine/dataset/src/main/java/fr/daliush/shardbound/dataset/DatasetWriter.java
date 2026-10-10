package fr.daliush.shardbound.dataset;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/** Writes records as JSON Lines, one per line, with snake_case keys for the Python tooling. */
public final class DatasetWriter {

    private final JsonMapper mapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    public String toJsonLines(List<?> entries) {
        return entries.stream().map(mapper::writeValueAsString).collect(Collectors.joining("\n", "", "\n"));
    }

    public void write(Path file, List<?> entries) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, toJsonLines(entries));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
