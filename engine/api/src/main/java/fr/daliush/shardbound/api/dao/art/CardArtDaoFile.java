package fr.daliush.shardbound.api.dao.art;

import fr.daliush.shardbound.api.dao.entities.art.CardArtEntity;
import fr.daliush.shardbound.api.dao.entities.art.CardArtImageEntity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The art of a folder, one image per card named by the card's id, {@code <card id>.webp} as
 * {@code scripts/publish-card-art.py} writes them. The folder is read at every call, so an image published while the
 * server runs is served at once. An image's version is its file's last change and size.
 */
public class CardArtDaoFile implements CardArtDao {

    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "webp", "image/webp", "png", "image/png", "jpg", "image/jpeg");

    private final Path artDir;

    /** A folder that does not exist yet holds no art. */
    public CardArtDaoFile(Path artDir) {
        this.artDir = artDir;
    }

    @Override
    public Optional<CardArtEntity> find(String cardId) {
        return findFile(cardId).map(path -> toEntity(cardId, path));
    }

    @Override
    public Optional<CardArtImageEntity> read(String cardId) {
        return findFile(cardId).map(path -> new CardArtImageEntity(toEntity(cardId, path), readBytes(path)));
    }

    /** The id asked is only compared with the files found, never made into a path: it can't leave the folder. */
    private Optional<Path> findFile(String cardId) {
        if (!Files.isDirectory(artDir)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.list(artDir)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> MEDIA_TYPES.containsKey(extension(path)) && baseName(path).equals(cardId))
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static CardArtEntity toEntity(String cardId, Path path) {
        try {
            String version = Long.toString(Files.getLastModifiedTime(path).toMillis(), 36)
                    + Long.toString(Files.size(path), 36);
            return new CardArtEntity(cardId, MEDIA_TYPES.get(extension(path)), version);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String extension(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private static String baseName(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
