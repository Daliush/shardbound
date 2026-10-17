package fr.daliush.shardbound.api.dao.evaluation;

import fr.daliush.shardbound.api.dao.entities.evaluation.DatasetEntity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The datasets of a datasets folder, {@code <version>/dataset-<split>.jsonl} as the Python split writes them. The
 * folder is read at every call, so a version built while the server runs is served at once.
 */
public class DatasetDaoFile implements DatasetDao {

    private static final String PREFIX = "dataset-";
    private static final String JSONL = ".jsonl";

    private final Path datasetsDir;

    /** One dataset file, named by where it sits. */
    private record DatasetFile(String version, String split, Path path) {
    }

    /** A folder that does not exist yet holds no dataset. */
    public DatasetDaoFile(Path datasetsDir) {
        this.datasetsDir = datasetsDir;
    }

    /** The names asked are only compared with the files found, never made into a path: they can't leave the folder. */
    @Override
    public Optional<DatasetEntity> find(String version, String split) {
        for (DatasetFile file : listDatasetFiles()) {
            if (file.version().equals(version) && file.split().equals(split)) {
                return Optional.of(new DatasetEntity(version, split, readJsonl(file.path())));
            }
        }
        return Optional.empty();
    }

    private List<DatasetFile> listDatasetFiles() {
        if (!Files.isDirectory(datasetsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(datasetsDir, 2)) {
            return paths.filter(path -> datasetsDir.relativize(path).getNameCount() == 2)
                    .filter(DatasetDaoFile::isDatasetFile)
                    .map(this::toDatasetFile)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isDatasetFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.startsWith(PREFIX) && fileName.endsWith(JSONL);
    }

    private DatasetFile toDatasetFile(Path path) {
        Path relative = datasetsDir.relativize(path);
        String fileName = relative.getName(1).toString();
        return new DatasetFile(relative.getName(0).toString(),
                fileName.substring(PREFIX.length(), fileName.length() - JSONL.length()), path);
    }

    private static String readJsonl(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
