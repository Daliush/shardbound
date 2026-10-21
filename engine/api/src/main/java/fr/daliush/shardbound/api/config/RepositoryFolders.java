package fr.daliush.shardbound.api.config;

import fr.daliush.shardbound.core.content.ContentLoader;
import java.nio.file.Path;
import java.util.Optional;

/** The folders the server reads: the one a property names, or else its place in the repository found above. */
final class RepositoryFolders {

    private RepositoryFolders() {
    }

    static Path find(Path configured, Path inRepository, String property) {
        Path here = Path.of("").toAbsolutePath();
        return Optional.ofNullable(configured)
                .or(() -> ContentLoader.find(here).map(content -> content.getParent().resolve(inRepository)))
                .orElseThrow(() -> new IllegalStateException("No repository found above " + here
                        + ": set " + property + " to the folder of " + inRepository));
    }
}
