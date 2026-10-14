package fr.daliush.shardbound.api.dao.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.EvaluationEntity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The evaluations of a reports folder, {@code <version>/<split>/<timestamp>.json} as the Python evaluation writes
 * them. The folder is read at every call, so an evaluation saved while the server runs is served at once. A bucket
 * will replace the folder when the server runs online, read the same way: list the names, then read one.
 */
public class EvaluationDaoFile implements EvaluationDao {

    private static final String JSON = ".json";

    private final Path reportsDir;

    /** One evaluation file, named by where it sits. */
    private record EvaluationFile(String version, String split, String timestamp, Path path) {

        boolean isOf(String version, String split) {
            return this.version.equals(version) && this.split.equals(split);
        }
    }

    /** A folder that does not exist yet holds no evaluation. */
    public EvaluationDaoFile(Path reportsDir) {
        this.reportsDir = reportsDir;
    }

    @Override
    public List<String> listTimestamps(String version, String split) {
        List<String> timestamps = new ArrayList<>();
        for (EvaluationFile file : listEvaluationFiles()) {
            if (file.isOf(version, split)) {
                timestamps.add(file.timestamp());
            }
        }
        Collections.sort(timestamps);
        return timestamps;
    }

    /** The names asked are only compared with the files found, never made into a path: they can't leave the folder. */
    @Override
    public Optional<EvaluationEntity> find(String version, String split, String timestamp) {
        for (EvaluationFile file : listEvaluationFiles()) {
            if (file.isOf(version, split) && file.timestamp().equals(timestamp)) {
                return Optional.of(new EvaluationEntity(version, split, timestamp, readJson(file.path())));
            }
        }
        return Optional.empty();
    }

    private List<EvaluationFile> listEvaluationFiles() {
        if (!Files.isDirectory(reportsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(reportsDir, 3)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(JSON))
                    .filter(path -> reportsDir.relativize(path).getNameCount() == 3)
                    .map(this::toEvaluationFile)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private EvaluationFile toEvaluationFile(Path path) {
        Path relative = reportsDir.relativize(path);
        String fileName = relative.getName(2).toString();
        return new EvaluationFile(relative.getName(0).toString(), relative.getName(1).toString(),
                fileName.substring(0, fileName.length() - JSON.length()), path);
    }

    private static String readJson(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
