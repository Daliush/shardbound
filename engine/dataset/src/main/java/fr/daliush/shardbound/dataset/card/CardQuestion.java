package fr.daliush.shardbound.dataset.card;

import java.util.List;

/**
 * One line of {@code cards.jsonl}: a factual question about one card and the key facts its data gives. {@code card}
 * decides the split, and it is the question's essential citation: the card's chunk (design doc §6.1).
 */
public record CardQuestion(String id, String category, String card, String question, List<String> keyFacts) {

    public CardQuestion {
        keyFacts = List.copyOf(keyFacts);
    }
}
