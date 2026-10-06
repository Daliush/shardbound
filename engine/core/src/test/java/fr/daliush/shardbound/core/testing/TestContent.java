package fr.daliush.shardbound.core.testing;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** The repository's real content, found by walking up from the working directory. */
public final class TestContent {

    private static Content content;

    private TestContent() {
    }

    public static synchronized Content content() {
        if (content == null) {
            content = ContentLoader.load(contentDir());
        }
        return content;
    }

    public static CardCatalog catalog() {
        return content().catalog();
    }

    public static Path contentDir() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve("content/cards/card.schema.json"))) {
                return dir.resolve("content");
            }
        }
        throw new IllegalStateException("No content/ folder above " + Path.of("").toAbsolutePath());
    }
}
