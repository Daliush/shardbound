package fr.daliush.shardbound.dataset;

import fr.daliush.shardbound.core.content.Content;
import fr.daliush.shardbound.core.content.ContentLoader;
import fr.daliush.shardbound.dataset.recipe.Recipes;
import java.nio.file.Path;

/**
 * Writes every question of a dataset version into {@code evals/datasets/<version>/}: {@code scenarios.jsonl},
 * {@code cards.jsonl}, {@code decks.jsonl}, {@code rules.jsonl} and {@code unanswerable.jsonl}. From {@code engine/}:
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
        writer.write(dir.resolve("rules.jsonl"), dataset.rules());
        writer.write(dir.resolve("unanswerable.jsonl"), dataset.unanswerable());
        System.out.println(dataset.scenarios().size() + " scenarios, " + dataset.cards().size() + " factual, "
                + dataset.decks().size() + " deck, " + dataset.rules().size() + " simple-rule and "
                + dataset.unanswerable().size() + " unanswerable questions written to " + dir);
    }
}
