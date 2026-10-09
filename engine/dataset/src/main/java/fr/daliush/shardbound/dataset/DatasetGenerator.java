package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.core.rules.GameEngine;
import fr.daliush.shardbound.dataset.recipe.Recipes;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes {@code evals/datasets/<version>/scenarios.jsonl} from the repository's content. From {@code engine/}:
 * {@code ./mvnw -q -pl dataset -am compile exec:java -Dexec.args=<version>}.
 */
public final class DatasetGenerator {

    private DatasetGenerator() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: DatasetGenerator <version>");
            System.exit(2);
        }
        Path contentDir = ContentLoader.find(Path.of(""))
                .orElseThrow(() -> new IllegalStateException("No content/ folder above " + Path.of("").toAbsolutePath()));
        Content content = ContentLoader.load(contentDir);
        Path root = contentDir.getParent();
        Rulebook rulebook = Rulebook.load(root.resolve("docs").resolve("rules"));
        List<DatasetEntry> entries = new DatasetBuilder(new GameEngine(content.catalog()), content.catalog(), rulebook)
                .entries(Recipes.all());
        Path file = root.resolve("evals").resolve("datasets").resolve(args[0]).resolve("scenarios.jsonl");
        new DatasetWriter().write(file, entries);
        System.out.println(entries.size() + " scenarios written to " + file);
    }
}
