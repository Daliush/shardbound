package fr.daliush.shardbound.dataset;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

/**
 * Questions written once and kept as they are, read from this module's resources: they are not written again for each
 * dataset version, only checked against the current rulebook and cards.
 */
public final class FrozenQuestions {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private FrozenQuestions() {
    }

    /** One record per line of the resource, keys in snake_case. */
    public static <T> List<T> read(String resource, Class<T> type) {
        try (InputStream in = FrozenQuestions.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No resource " + resource);
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> MAPPER.readValue(line, type))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
