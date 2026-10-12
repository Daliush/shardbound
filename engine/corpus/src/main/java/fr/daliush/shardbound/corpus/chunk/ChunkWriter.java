package fr.daliush.shardbound.corpus.chunk;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.json.JsonMapper;

/** Writes chunks as JSON Lines, one per line, with lowercase kinds for the Python index. */
public final class ChunkWriter {

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(EnumFeature.WRITE_ENUMS_TO_LOWERCASE)
            .build();

    public String toJsonLines(List<Chunk> chunks) {
        return chunks.stream().map(mapper::writeValueAsString).collect(Collectors.joining("\n", "", "\n"));
    }

    public void writeJsonLines(Path file, List<Chunk> chunks) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, toJsonLines(chunks));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
