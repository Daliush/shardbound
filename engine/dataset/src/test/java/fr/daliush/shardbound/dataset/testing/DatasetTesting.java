package fr.daliush.shardbound.dataset.testing;

import fr.daliush.shardbound.core.content.CardCatalog;
import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.dataset.DatasetBuilder;
import fr.daliush.shardbound.dataset.DatasetEntry;
import fr.daliush.shardbound.dataset.Rulebook;
import fr.daliush.shardbound.dataset.recipe.Recipe;
import java.nio.file.Path;
import java.util.List;

/** The repository's real content and rulebook, found by walking up from the working directory, and the builder. */
public final class DatasetTesting {

    private static final Path CONTENT_DIR = ContentLoader.find(Path.of("")).orElseThrow();

    public static final Content CONTENT = ContentLoader.load(CONTENT_DIR);
    public static final CardCatalog CATALOG = CONTENT.catalog();
    public static final GameEngine ENGINE = new GameEngine(CATALOG);
    public static final Rulebook RULEBOOK = Rulebook.load(CONTENT_DIR.getParent().resolve("docs/rules"));

    private DatasetTesting() {
    }

    public static List<DatasetEntry> entries(Recipe... recipes) {
        return new DatasetBuilder(ENGINE, CATALOG, RULEBOOK).entries(List.of(recipes));
    }
}
