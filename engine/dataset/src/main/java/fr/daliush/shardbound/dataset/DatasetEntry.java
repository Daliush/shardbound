package fr.daliush.shardbound.dataset;

import java.util.List;

/**
 * One line of the dataset: a scenario, its template question and its answer key. {@code interaction} decides the
 * split; {@code cards} are every card of the scenario, so cards can be held out between train and test (design doc
 * §6.1). The essential rules are {@code rules}, from the engine's events, and {@code contextRules}, from the recipe.
 */
public record DatasetEntry(
        String id,
        String category,
        String interaction,
        String question,
        List<String> cards,
        List<String> keyFacts,
        List<String> rules,
        List<String> contextRules,
        List<String> trace) {

    public DatasetEntry {
        cards = List.copyOf(cards);
        keyFacts = List.copyOf(keyFacts);
        rules = List.copyOf(rules);
        contextRules = List.copyOf(contextRules);
        trace = List.copyOf(trace);
    }
}
