package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.dataset.recipe.Recipes;
import java.nio.file.Path;

/**
 * Writes {@code scenarios.jsonl}, {@code cards.jsonl} and {@code decks.jsonl} into {@code evals/datasets/<version>/}
 * from the repository's content. From {@code engine/}:
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
        Path contentDir = ContentLoader.find(Path.of("")).orElseThrow(
                () -> new IllegalStateException("No content/ folder above " + Path.of("").toAbsolutePath()));
        Content content = ContentLoader.load(contentDir);
        Path root = contentDir.getParent();
        Dataset dataset = Dataset.build(content, Rulebook.load(root.resolve("docs").resolve("rules")), Recipes.all());
        Path dir = root.resolve("evals").resolve("datasets").resolve(args[0]);
        DatasetWriter writer = new DatasetWriter();
        writer.write(dir.resolve("scenarios.jsonl"), dataset.scenarios());
        writer.write(dir.resolve("cards.jsonl"), dataset.cards());
        writer.write(dir.resolve("decks.jsonl"), dataset.decks());
        System.out.println(dataset.scenarios().size() + " scenarios, " + dataset.cards().size()
                + " factual questions and " + dataset.decks().size() + " deck questions written to " + dir);
    }
}
