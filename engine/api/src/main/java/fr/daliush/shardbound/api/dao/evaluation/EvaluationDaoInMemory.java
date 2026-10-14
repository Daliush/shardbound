package fr.daliush.shardbound.api.dao.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.EvaluationEntity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The evaluations of a reports folder, {@code <version>/<split>/<timestamp>.json} as the Python evaluation writes
 * them, loaded once into this process's memory. A bucket will replace the folder when the server runs online.
 */
public class EvaluationDaoInMemory implements EvaluationDao {

    private static final String JSON = ".json";

    /** Timestamps sort as text the way they sort in time, so each split's evaluations stay in order. */
    private final Map<DatasetSplit, TreeMap<String, EvaluationEntity>> evaluations = new HashMap<>();

    private record DatasetSplit(String version, String split) {
    }

    /** A folder that does not exist yet holds no evaluation. */
    public EvaluationDaoInMemory(Path reportsDir) {
        for (EvaluationEntity evaluation : readEvaluations(reportsDir)) {
            DatasetSplit split = new DatasetSplit(evaluation.version(), evaluation.split());
            evaluations.computeIfAbsent(split, ignored -> new TreeMap<>()).put(evaluation.timestamp(), evaluation);
        }
    }

    @Override
    public List<String> listTimestamps(String version, String split) {
        return List.copyOf(evaluations.getOrDefault(new DatasetSplit(version, split), new TreeMap<>()).keySet());
    }

    @Override
    public Optional<EvaluationEntity> find(String version, String split, String timestamp) {
        return Optional.ofNullable(evaluations.getOrDefault(new DatasetSplit(version, split), new TreeMap<>())
                .get(timestamp));
    }

    private static List<EvaluationEntity> readEvaluations(Path reportsDir) {
        if (!Files.isDirectory(reportsDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(reportsDir, 3)) {
            return files.filter(file -> file.getFileName().toString().endsWith(JSON))
                    .filter(file -> reportsDir.relativize(file).getNameCount() == 3)
                    .map(EvaluationDaoInMemory::readEvaluation)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static EvaluationEntity readEvaluation(Path file) {
        String fileName = file.getFileName().toString();
        try {
            return new EvaluationEntity(file.getParent().getParent().getFileName().toString(),
                    file.getParent().getFileName().toString(),
                    fileName.substring(0, fileName.length() - JSON.length()),
                    Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
